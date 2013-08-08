package de.codesourcery.voxelgame.core.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.StringUtils;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.render.IChunkRenderer;
import de.codesourcery.voxelgame.core.util.ChunkList;
import de.codesourcery.voxelgame.core.util.ChunkList.ChunkListEntry;
import de.codesourcery.voxelgame.core.world.Chunk.ChunkKey;


public class DefaultChunkManager implements IChunkManager 
{
	// the current chunk (3x3 chunks)
	public static final int MAX_CACHED_CHUNKS = 90;

	private final Vector3 TMP = new Vector3();

	private final ChunkList chunkList = new ChunkList(); // holds all cached chunks

	// @GuardedBy( chunkMap )
	private final Map<ChunkKey,Chunk> chunkMap = new HashMap<>(); // all cached chunks, indexed by position 

	private final List<Chunk> bgVisibleChunks = new ArrayList<>(); // holds all chunks that are currently part of the view frustum

	// @GuardedBy( fgVisibleChunks )
	private final List<Chunk> fgVisibleChunks = new ArrayList<>(); // holds all chunks that are currently part of the view frustum	

	private IChunkStorage chunkStorage;

	public final Camera camera;

	// the chunk the camera currently is in

	// the camera is always located in the CENTER chunk of the 3x3 chunk

	// chunks are layed out in a cartesian coordinate system
	// with the z-axis pointing up and the x-axis pointing right
	// world coordinates (0,0,0) are right in the center of chunk (0,0)

	public int cameraChunkX = 0;
	public int cameraChunkY = 0;	
	public int cameraChunkZ = 0;

	private final AtomicBoolean reloadWholeChunk= new AtomicBoolean(true);
	private final AtomicBoolean rebuildVisibleList = new AtomicBoolean( false);

	private long accessCounter = 0;

	private IChunkRenderer chunkRenderer;

	private final ThreadPoolExecutor workerPool = createWorkerPool();

	public DefaultChunkManager(Camera camera) 
	{
		this.camera = camera;
		cameraMoved();
	}

	public void setChunkRenderer(IChunkRenderer chunkRenderer) {
		this.chunkRenderer = chunkRenderer;
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#getCurrentChunk()
	 */
	@Override
	public void visitVisibleChunks(IChunkVisitor visitor) 
	{
		synchronized( fgVisibleChunks ) 
		{
			for ( Chunk chunk : internalGetVisibleChunks() ) 
			{
				visitor.visit( chunk );
			}
		}
	}
	
	private long lastDebug = 0;

	private synchronized List<Chunk> internalGetVisibleChunks() 
	{
		accessCounter++;		
		if ( rebuildVisibleList.get() || reloadWholeChunk.get() ) 
		{
			long loadTime = 0;
			long listUpdateTime = 0;
			if ( reloadWholeChunk.get() ) 
			{
				loadTime = -System.currentTimeMillis();
				reloadChunks();
				loadTime += System.currentTimeMillis();
				
				listUpdateTime = -System.currentTimeMillis();
				updateVisibleChunksList();
				listUpdateTime += System.currentTimeMillis();
			} 
			else if ( rebuildVisibleList.get() ) 
			{
				listUpdateTime = -System.currentTimeMillis();				
				updateVisibleChunksList();
				listUpdateTime += System.currentTimeMillis();
			}

			synchronized( fgVisibleChunks ) {
				fgVisibleChunks.clear();
				fgVisibleChunks.addAll( bgVisibleChunks );
			}
			
			if ( ( accessCounter - lastDebug ) >= 60 ) {
				System.out.println("Chunk reload time: "+loadTime+" ms , visible_list_update: "+listUpdateTime+" ms");
				lastDebug = accessCounter;
			}
		}
		return fgVisibleChunks;
	}

	private void reloadChunks() 
	{
		System.out.println("Reloading chunks around: "+cameraChunkX+" / "+cameraChunkY+" / "+cameraChunkZ);

		synchronized ( chunkMap ) 
		{
			for ( Chunk chunk : chunkMap.values() ) {
				chunk.setPinned(false);
			}
			for ( int deltaX = -3 ; deltaX <= 3 ; deltaX++ ) 
			{
				for ( int deltaZ = -3 ; deltaZ <= 3 ; deltaZ++ ) 
				{
					getChunk( cameraChunkX + deltaX , 0 , cameraChunkZ+deltaZ ).setFlags(Chunk.FLAG_PINNED);
				}			
			}
		}
		reloadWholeChunk.set( false );	
	}

	@Override
	public Chunk maybeGetChunk(int chunkX, int chunkY, int chunkZ) 
	{
		final ChunkKey key = new ChunkKey(chunkX,chunkY,chunkZ);
		synchronized (chunkMap) {
			Chunk newChunk = chunkMap.get( key );
			if ( newChunk != null ) 
			{
				newChunk.accessCounter = accessCounter;
				return newChunk;
			}
		}
		return null;
	}

	@Override
	public Chunk getChunk(int chunkX, int chunkY, int chunkZ) 
	{		
		final ChunkKey key = new ChunkKey(chunkX,chunkY,chunkZ);
		Chunk newChunk = null;
		synchronized( chunkMap ) 
		{
			newChunk = chunkMap.get( key );
			if ( newChunk != null ) 
			{
				newChunk.accessCounter = accessCounter;
				return newChunk;
			}

			try {
				newChunk = chunkStorage.loadChunk( chunkX , chunkY , chunkZ );
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}

			newChunk.accessCounter = accessCounter;	

			if ( chunkMap.size() > MAX_CACHED_CHUNKS ) 
			{
				ChunkListEntry toRemovePrevious = null;
				ChunkListEntry toRemove = null;
				ChunkListEntry previous = null;
				ChunkListEntry entry = chunkList.head;
				while ( entry != null ) 
				{
					Chunk tmp = entry.chunk;
					if ( tmp.isInvisible() && tmp.isNotPinned() ) 
					{
						if ( toRemove == null || tmp.accessCounter < toRemove.chunk.accessCounter ) {
							toRemovePrevious = previous;
							toRemove = entry;
						}
					}
					previous = entry;
					entry = entry.next;
				}

				if ( toRemove != null ) 
				{
					Chunk chunkToRemove = toRemove.chunk;

					//			System.out.println("*** Disposing chunk "+chunkToRemove+" with access count "+chunkToRemove.accessCounter);

					chunkList.remove( toRemove , toRemovePrevious );
					chunkMap.remove( new ChunkKey( chunkToRemove ) );
					bgVisibleChunks.remove( chunkToRemove );
					try {
						chunkStorage.unloadChunk( chunkToRemove );
					} 
					catch (IOException e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
					chunkToRemove.dispose();
				} else {
					System.out.println("INFO: Extending chunk chache, cache size is now: "+chunkMap.size());
				}
			}

			chunkMap.put( key , newChunk );
			chunkList.add( newChunk );
		}
		return newChunk;
	}	

	protected static final class DynamicLatch 
	{
		private final Object LOCK = new Object();
		private int waitCount=0;
		private boolean notifyCalled = false;

		private final ReentrantLock USER_LOCK = new ReentrantLock();

		public DynamicLatch() {
		}

		public void lock() {
			USER_LOCK.lock();
		}

		public void unlock() {
			USER_LOCK.unlock();
		}		

		public void reset() 
		{
			synchronized(LOCK) 
			{
				waitCount=0;
				notifyCalled = true;

				LOCK.notifyAll();

				notifyCalled = false;				
			}
		}

		public void countUp() 
		{
			synchronized(LOCK) 
			{
				waitCount++;	
			}
		}

		public void countDown() 
		{
			synchronized(LOCK) 
			{
				if ( waitCount > 0 ) 
				{
					waitCount--;
					if ( waitCount == 0 )
					{
						notifyCalled=true;
						LOCK.notifyAll();
					}
				}
			}
		}

		public void await() throws InterruptedException 
		{
			synchronized(LOCK) 
			{			
				while ( waitCount != 0 )
				{
					LOCK.wait(3*1000);
					if ( ! notifyCalled ) {
						System.err.println("Latch#await() returned either because 3 second timeout elapsed or a spurious wakeup occured");
					}
				}
			}
		}
	}

	private final DynamicLatch latch = new DynamicLatch();

	private void updateVisibleChunksList() 
	{
		bgVisibleChunks.clear();

		latch.lock();
		try 
		{
			latch.reset();

			ChunkListEntry current = chunkList.head;
			while( current != null ) 
			{
				final Chunk chunk=current.chunk;
				final boolean isVisible = ! chunk.isEmpty() && camera.frustum.boundsInFrustum( chunk.bb );
				chunk.setVisible( isVisible );
				if ( isVisible ) 
				{
					if ( chunk.isMeshRebuildRequired() || chunk.isLightRecalculationRequired() ) {
						updateChunk( chunk , latch );
					}
					bgVisibleChunks.add( chunk );
				} 
				current = current.next;
			}

			try 
			{
				latch.await();
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			rebuildVisibleList.set( false );
		} 
		finally 
		{
			latch.unlock();
		}
	}

	protected ThreadPoolExecutor createWorkerPool() 
	{
		int threadCount = Runtime.getRuntime().availableProcessors();
		if ( threadCount > 2 ) {
			threadCount -= 2; // reserve two CPU cores for libgdx rendering and UI thread
		} else {
			threadCount=1;
		}
		final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(150);
		final ThreadFactory threadFactory = new ThreadFactory() {

			private final AtomicInteger threadCounter = new AtomicInteger(1);

			@Override
			public Thread newThread(Runnable r) 
			{
				final Thread result = new Thread(r);
				result.setDaemon( true );
				result.setName( "chunk-manager-worker-"+threadCounter.incrementAndGet());
				return result;
			}};
			return new ThreadPoolExecutor( threadCount, threadCount, 60, TimeUnit.SECONDS , queue , threadFactory , new CallerRunsPolicy() );
	}

	public void updateChunk(final Chunk chunk) 
	{
		synchronized(chunk) 
		{
			if ( chunk.isLightRecalculationRequired() ) {
				recalculateLighting(chunk);
			}
			if ( chunk.isMeshRebuildRequired() ) {
				chunkRenderer.setupMesh( chunk );
			}		
		}
	}

	private void updateChunk(final Chunk chunk,final DynamicLatch latch) 
	{
		latch.countUp();
		workerPool.submit( new Runnable() 
		{
			@Override
			public void run() 
			{
				try 
				{
					updateChunk(chunk);
				} 
				finally 
				{
					latch.countDown();
				}
			}} );
	}

	private void recalculateLighting(Chunk chunk) 
	{
		final Block[][][] blocks = chunk.blocks;
		// set light level of blocks that are directly hit by sunlight (no opaque block above them)
		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) {
			for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 
			{
				// start out with max. light level
				// TODO: If we have more than one chunk on the Y axis, we will need to check chunks on top of the current one as well... 				
				byte currentLightLevel = Block.MAX_LIGHT_LEVEL;
				for ( int y1 = Chunk.BLOCKS_Y-1 ; y1 >= 0; y1-- ) 
				{
					final Block block = blocks[x][y1][z];
					block.lightLevel = currentLightLevel;
					if ( ! block.isTranslucentBlock() ) { // blocks below it will only receive min light level
						currentLightLevel = Block.MIN_LIGHT_LEVEL; 
					} 
				}				
			}			
		}
		chunk.setLightRecalculationRequired(false);
	}

	protected static void printChunk(List<Chunk> currentChunks) 
	{
		System.out.print( " | "+StringUtils.rightPad( currentChunks.get(0).toString() , 15 ) );
		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 1 ).toString() , 15 ) );
		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 2 ).toString() , 15 ) );
		System.out.println();

		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 3 ).toString() , 15 ) );
		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 4 ).toString() , 15 ) );
		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 5 ).toString() , 15 ) );
		System.out.println();

		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 6 ).toString() , 15 ) );
		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 7 ).toString() , 15 ) );
		System.out.print( " | "+StringUtils.rightPad( currentChunks.get( 8 ).toString() , 15 ) );
		System.out.println();		
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#moveCameraRelative(float, float)
	 */
	@Override
	public void cameraMoved()
	{
		// world (0,0) is at center of chunk at (0,0), adjust coordinates by half chunk size
		float trueX = camera.position.x+Chunk.HALF_CHUNK_WIDTH;
		float trueY = (camera.position.y+Chunk.HALF_CHUNK_HEIGHT);		
		float trueZ = (camera.position.z+Chunk.HALF_CHUNK_DEPTH);

		// chunk 'world' coordinates 
		int newCameraChunkX = (int) Math.floor(trueX / Chunk.CHUNK_WIDTH);
		int newCameraChunkY = (int) Math.floor(trueY / Chunk.CHUNK_HEIGHT);		
		int newCameraChunkZ = (int) Math.floor(trueZ / Chunk.CHUNK_DEPTH);

		if ( newCameraChunkX == cameraChunkX && newCameraChunkY == cameraChunkY && newCameraChunkZ == cameraChunkZ) 
		{
			// camera still on same chunk
			rebuildVisibleList.set(true);
			return;
		}

		System.out.println("*** CAMERA: Now at chunk ( "+this.cameraChunkX+","+this.cameraChunkY+","+this.cameraChunkZ+") ***");
		this.cameraChunkX = newCameraChunkX;
		this.cameraChunkY = newCameraChunkY;
		this.cameraChunkZ = newCameraChunkZ;		

		reloadWholeChunk.set(true);		
	}

	public static final class Hit 
	{
		public Chunk chunk;
		public int blockX;
		public int blockY;
		public int blockZ;
		public final Vector3 hitPointOnBlock=new Vector3();

		public Block getBlock() {
			return chunk.blocks[blockX][blockY][blockZ];
		}

		@Override
		public String toString() {
			return "HIT: "+chunk+" at block ("+blockX+","+blockY+","+blockZ+")";
		}
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#getClosestIntersection(com.badlogic.gdx.math.collision.Ray, de.codesourcery.voxelgame.core.world.ChunkManager.Hit)
	 */
	@Override
	public boolean getClosestIntersection(Ray ray,Hit hit) {

		boolean gotHit = false;
		float distanceToHitSquared =0;

		Vector3 blockHit = new Vector3(); // block((int) x,(int) y,(int) z) coordinates inside chunk being hit

		synchronized( fgVisibleChunks ) 
		{
			for ( Chunk chunk : internalGetVisibleChunks() ) 
			{
				if ( chunk.intersects( ray ) && chunk.getClosestIntersection( ray , blockHit , hit.hitPointOnBlock ) ) 
				{ 
					float distance = hit.hitPointOnBlock.dst2( ray.origin );
					if ( ! gotHit || distance < distanceToHitSquared ) 
					{
						gotHit = true;
						TMP.set( hit.hitPointOnBlock );
						hit.chunk = chunk;
						hit.blockX = (int) blockHit.x;
						hit.blockY = (int) blockHit.y;
						hit.blockZ = (int) blockHit.z;
						distanceToHitSquared = distance;
					}
				}
			}
		}
		if ( gotHit ) {
			hit.hitPointOnBlock.set( TMP );
		}
		return gotHit;
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#getContainingBlock(com.badlogic.gdx.math.Vector3, de.codesourcery.voxelgame.core.world.ChunkManager.Hit)
	 */
	@Override
	public boolean getContainingBlock(Vector3 worldCoords,Hit hit) 
	{
		synchronized( fgVisibleChunks ) 
		{
			for ( Chunk chunk : internalGetVisibleChunks() ) 
			{
				if ( chunk.containsPoint( worldCoords ) ) 
				{
					hit.chunk = chunk;
					if ( chunk.getBlockContaining(worldCoords,hit) ) {
						return true;
					} 
					System.out.println("ERROR: Failed to find block containing "+worldCoords+" although "+chunk+" contains point "+worldCoords);
				}
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#intersectsNonEmptyBlock(com.badlogic.gdx.math.collision.BoundingBox)
	 */
	@Override
	public boolean intersectsNonEmptyBlock(BoundingBox bb)
	{
		synchronized( fgVisibleChunks ) 
		{		
			for ( Chunk chunk : internalGetVisibleChunks() ) 
			{
				if ( chunk.intersectsNonEmptyBlock( bb ) ) 
				{
					return true;
				}
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#setChunkManager(de.codesourcery.voxelgame.core.world.ChunkManager)
	 */
	@Override
	public void setChunkStorage(IChunkStorage chunkManager) {
		this.chunkStorage = chunkManager;
	}

	@Override
	public void dispose() 
	{
		synchronized( chunkMap ) 
		{
			for (Iterator<Entry<ChunkKey, Chunk>> it = chunkMap.entrySet() .iterator(); it.hasNext();) 
			{
				final Entry<ChunkKey, Chunk> chunk = it.next();
				chunk.getValue().dispose();
				it.remove();
			}
		}

		synchronized( fgVisibleChunks ) {
			fgVisibleChunks.clear();
		}
		bgVisibleChunks.clear();
	}
}