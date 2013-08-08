package de.codesourcery.voxelgame.core.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.StringUtils;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.IntMap;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.render.IChunkRenderer;
import de.codesourcery.voxelgame.core.world.Chunk.ChunkKey;


public class DefaultChunkManager implements IChunkManager 
{
	// the current chunk (3x3 chunks)
	public static final int MAX_CACHED_CHUNKS = 100;

	private final Vector3 TMP = new Vector3();

	private final ArrayList<Chunk> chunkList = new ArrayList<Chunk>(MAX_CACHED_CHUNKS); // holds all cached chunks

	// @GuardedBy( chunkMap )
	private final IntMap<Chunk> chunkMap = new IntMap<>(); // all cached chunks, indexed by position 

	private final AtomicReference<ArrayList<Chunk>> visibleChunks = new AtomicReference<>( new ArrayList<Chunk>() ); // holds all chunks that are currently part of the view frustum	

	private final IChunkStorage chunkStorage;

	public final Camera camera;

	// the chunk the camera currently is in

	// the camera is always located in the CENTER chunk of the 3x3 chunk

	// chunks are layed out in a cartesian coordinate system
	// with the z-axis pointing up and the x-axis pointing right
	// world coordinates (0,0,0) are right in the center of chunk (0,0)

	public int cameraChunkX = 0;
	public int cameraChunkY = 0;	
	public int cameraChunkZ = 0;

	private long accessCounter = 0;

	private IChunkRenderer chunkRenderer;

	private final ThreadPoolExecutor loaderThreadsPool;
	private final ThreadPoolExecutor updaterThreadsPool;

	private final ChunkUpdaterThread chunkUpdater;
	private final ChunkLoaderThread chunkLoader;

	protected final class ChunkUpdaterThread extends Thread {

		private final Chunk BLUE_PILL = Chunk.createNULLChunk();

		private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(200);

		public ChunkUpdaterThread() 
		{
			setDaemon(true);
			setName("chunk-loader");
		}

		public void queueChunkUpdate(Chunk chunk) 
		{
			synchronized ( queue ) 
			{
				for ( Chunk c : queue ) 
				{
					if ( c.equals( chunk ) ) 
					{
						return;
					}
				}
				queue.add( chunk );
			}
		}

		@Override
		public void run() 
		{
			while ( true ) 
			{
				final Chunk chunk;
				try {
					chunk = queue.take();
				} 
				catch (InterruptedException e) {
					e.printStackTrace();
					continue;
				}
				if ( chunk == BLUE_PILL ) {
					break;
				}
				runAsyncUpdateChunk( chunk );
			}
		}

		private void runAsyncUpdateChunk(final Chunk chunk) 
		{
			updaterThreadsPool.submit( new Runnable() 
			{
				@Override
				public void run() 
				{
					synchronized( chunk ) 
					{
						if ( chunk.isMeshRebuildRequired() && ! chunk.isDisposed() ) 
						{				
							syncUpdateChunk(chunk);
							updateVisibleChunksList();
						}
					}
				}} );
		}		

		public void terminate() 
		{
			synchronized(queue) 
			{
				try {
					queue.put( BLUE_PILL );
				} 
				catch (InterruptedException e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
				}
			}
		}
	}	

	protected final class ChunkLoaderThread extends Thread {

		private final ChunkKey BLUE_PILL = new ChunkKey(0,0,0);

		private final ArrayBlockingQueue<ChunkKey> queue = new ArrayBlockingQueue<>(200);

		public ChunkLoaderThread() 
		{
			setDaemon(true);
			setName("chunk-loader");
		}

		public void queueChunkLoad(ChunkKey key) 
		{
			synchronized ( queue ) 
			{
				for ( ChunkKey c : queue ) 
				{
					if ( c.equals( key ) ) {
						return;
					}
				}
				queue.add( key );
			}
		}

		@Override
		public void run() 
		{
			while ( true ) 
			{
				final ChunkKey key;
				try {
					key = queue.take();
				} 
				catch (InterruptedException e) {
					e.printStackTrace();
					continue;
				}
				if ( key == BLUE_PILL ) {
					break;
				}
				asyncLoadChunk( key.x , key.y , key.z );
			}
		}

		private void asyncLoadChunk(final int chunkX, final int chunkY, final int chunkZ) 
		{
			loaderThreadsPool.submit( new Runnable() {

				@Override
				public void run() 
				{
					final int chunkKey = Chunk.calcChunkKey(chunkX, chunkY, chunkZ);
					Chunk existing;
					synchronized( chunkMap ) 
					{
						existing = chunkMap.get( chunkKey );
					}
					if ( existing == null )
					{
						existing = syncLoadChunk(chunkX, chunkY, chunkZ, chunkKey);
					}
				}} );
		}		

		public void terminate() 
		{
			synchronized(queue) 
			{
				try {
					queue.put( BLUE_PILL );
				} 
				catch (InterruptedException e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	public DefaultChunkManager(Camera camera,IChunkStorage chunkStorage) 
	{
		this.camera = camera;
		this.chunkStorage = chunkStorage;

		int cpuCount = Runtime.getRuntime().availableProcessors();
		this.loaderThreadsPool=createWorkerPool( cpuCount , true );
		this.updaterThreadsPool = createWorkerPool( cpuCount , true );

		chunkLoader = new ChunkLoaderThread();
		chunkLoader.start();

		chunkUpdater = new ChunkUpdaterThread();
		chunkUpdater.start();
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
		for ( Chunk chunk : internalGetVisibleChunks() ) 
		{
			synchronized( chunk ) 
			{
				if ( ! chunk.isDisposed() ) {
					visitor.visit( chunk );
				}
			}
		}
	}

	/**
	 * Must ALWAYS be called while synchronized(fgVisibleChunks)
	 * @return
	 */
	private List<Chunk> internalGetVisibleChunks() 
	{
		accessCounter++;
		return visibleChunks.get();
	}

	private final Vector3 TMP_V1 = new Vector3();
	private final Vector3 TMP_V2 = new Vector3();

	private void cameraMovedToNewChunk() 
	{
		// System.out.println("Reloading chunks around: "+cameraChunkX+" / "+cameraChunkY+" / "+cameraChunkZ);
		final List<ChunkKey> toLoad = new ArrayList<>();
		synchronized ( chunkMap ) 
		{
			for ( int deltaX = -5 ; deltaX <= 5 ; deltaX++ ) 
			{
				for ( int deltaZ = -5 ; deltaZ <= 5 ; deltaZ++ ) 
				{
					final int key = Chunk.calcChunkKey(cameraChunkX + deltaX , 0 , cameraChunkZ+deltaZ);
					if ( ! chunkMap.containsKey( key ) )
					{
						toLoad.add( new ChunkKey( cameraChunkX + deltaX , 0 , cameraChunkZ+deltaZ ) );
					} 
				}			
			}
		}

		if ( ! toLoad.isEmpty() ) 
		{ 
			// schedule chunk loading by proximity to view direction
			final Vector3 pointInCameraDirection = new Vector3(camera.direction).scl( 1000 );
			pointInCameraDirection.add( camera.position );

			Collections.sort( toLoad , new Comparator<ChunkKey>()  
					{
				@Override
				public int compare(ChunkKey o1, ChunkKey o2) 
				{
					o1.populateCenter( TMP_V1 );
					o2.populateCenter( TMP_V2 );
					float dist1 = TMP_V1.dst2( pointInCameraDirection );
					float dist2 = TMP_V2.dst2( pointInCameraDirection );
					if ( dist1 < dist2 ) {
						return -1;
					} 
					if ( dist1 > dist2 ) {
						return 1; 
					}
					return 0;
				}
					});

			for ( ChunkKey key : toLoad ) 
			{
				chunkLoader.queueChunkLoad( key );
			}			
			// updateVisibleChunksList() will be called after each chunk load
			// so no need to do it here
		} 
		else 
		{
			// no chunks loaded but since this method gets called by cameraMoved()
			// the view frustum has changed and thus an already loaded chunk 
			// might've become visible
			updateVisibleChunksList();
		}
	}

	@Override
	public Chunk maybeGetChunk(int chunkX, int chunkY, int chunkZ) 
	{
		final int key = Chunk.calcChunkKey(chunkX,chunkY,chunkZ);
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
		final int key = Chunk.calcChunkKey(chunkX, chunkY, chunkZ);
		synchronized( chunkMap ) 
		{
			Chunk newChunk = chunkMap.get( key );
			if ( newChunk != null ) 
			{
				newChunk.accessCounter = accessCounter;
				return newChunk;
			}
		}
		return syncLoadChunk(chunkX,chunkY,chunkZ , key );
	}	

	private Chunk syncLoadChunk(int chunkX, int chunkY, int chunkZ,int chunkKey) 
	{
		// load chunk
		final Chunk newChunk;
		try {
			newChunk = chunkStorage.loadChunk( chunkX , chunkY , chunkZ );
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		// check chunk visibility
		final boolean chunkVisible = this.camera.frustum.boundsInFrustum( newChunk.boundingBox );
		newChunk.setVisible(  chunkVisible );

		// loadChunk() ( disk access!) might've taken a long time, check whether a concurrent load happened already
		Chunk existing=null;		
		synchronized( chunkMap ) 
		{		
			existing = chunkMap.get( chunkKey );
		}

		// update visible chunks BEFORE adding it them to the internal map
		// so the render thread does not see chunks that require a rebuid
		if ( existing == null && chunkVisible ) 
		{
			syncUpdateChunk(newChunk);
		} 

		Chunk chunkToEvict = null;		
		synchronized( chunkMap ) 
		{		
			// updateChunk() takes a long time, check if another caller finished in the meantime ...
			existing = chunkMap.get( chunkKey );
			if ( existing == null ) 
			{
				synchronized( chunkList ) 
				{				
					if ( chunkMap.size > MAX_CACHED_CHUNKS ) 
					{
						int indexToRemove = -1;
						int index = 0;
						for ( Iterator<Chunk> it = chunkList.iterator() ; it.hasNext(); index++ ) 
						{
							final Chunk chunk = it.next();
							if ( chunk.isInvisible() ) 
							{
								if ( chunkToEvict == null || chunk.accessCounter < chunkToEvict.accessCounter ) 
								{
									indexToRemove=index;
									chunkToEvict=chunk;
								}
							}						
						}
						if ( chunkToEvict != null ) 
						{
							chunkMap.remove( Chunk.calcChunkKey( chunkToEvict ) );							
							chunkList.remove( indexToRemove );
						} else {
							System.out.println("INFO: Extending chunk chache, cache size is now: "+chunkMap.size);
						}
					}
					newChunk.accessCounter = accessCounter;
					chunkMap.put( chunkKey , newChunk );
					chunkList.add( newChunk );					
				}

			}
		}

		if ( chunkToEvict != null ) 
		{
			try 
			{
				synchronized(chunkToEvict) 
				{
					chunkStorage.unloadChunk( chunkToEvict );
				}
			} 
			catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}			
		}

		if ( existing != null ) 
		{
			System.err.println("-- Disposing chunk, concurrent update already created another instance");
			try 
			{
				chunkStorage.unloadChunk( newChunk );
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
			return existing;
		}


		if ( chunkVisible ) 
		{
			addVisibleChunks();
		} 
		else 
		{
			// chunk is not visible (yet) , schedule asynchronous update
			queueAsyncChunkUpdate( newChunk );			
		}
		return newChunk;
	}

	private void queueAsyncChunkUpdate(Chunk chunk) {
		chunkUpdater.queueChunkUpdate( chunk );
	}
	
	private void addVisibleChunks()
	{
		final ArrayList<Chunk> tmpList=new ArrayList<Chunk>();
		synchronized( chunkList ) 
		{
			for ( Chunk chunk : chunkList) 
			{ 
				if ( chunk.isVisible() && chunk.isNotDisposed() && !chunk.isMeshRebuildRequired() ) {
					{
						tmpList.add( chunk );
					}
				}
			}
		}
		visibleChunks.set(tmpList);
	}

	private void updateVisibleChunksList() 
	{
		final ArrayList<Chunk> tmpList = new ArrayList<>();
		synchronized( chunkList ) 
		{
			for ( Chunk chunk : chunkList) 
			{
				final boolean isVisible = ! chunk.isEmpty() && camera.frustum.boundsInFrustum( chunk.boundingBox );
				chunk.setVisible( isVisible );
				if ( isVisible && ! chunk.isDisposed() ) 
				{
					if ( chunk.isMeshRebuildRequired() ) 
					{
						queueAsyncChunkUpdate( chunk );
					} else {
						tmpList.add( chunk );
					}
				} 
			}
		}
		visibleChunks.set(tmpList);
	}

	protected ThreadPoolExecutor createWorkerPool(int threadCount,boolean fifo) 
	{
		final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(350,fifo);
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

	@Override
	public void chunkChanged(Chunk chunk) 
	{
		queueAsyncChunkUpdate( chunk );
	}

	private void syncUpdateChunk(final Chunk chunk) 
	{
		synchronized(chunk) 
		{
			recalculateLighting(chunk);				
			chunkRenderer.setupMesh( chunk );
		}
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
			// camera still on same chunk but view frustum has changed			
			updateVisibleChunksList();
			return; 
		}

		// System.out.println("*** CAMERA: Now at chunk ( "+this.cameraChunkX+","+this.cameraChunkY+","+this.cameraChunkZ+") ***");
		this.cameraChunkX = newCameraChunkX;
		this.cameraChunkY = newCameraChunkY;
		this.cameraChunkZ = newCameraChunkZ;		

		cameraMovedToNewChunk();
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
		return false;
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#intersectsNonEmptyBlock(com.badlogic.gdx.math.collision.BoundingBox)
	 */
	@Override
	public boolean intersectsNonEmptyBlock(BoundingBox bb)
	{
		for ( Chunk chunk : internalGetVisibleChunks() ) 
		{
			if ( chunk.intersectsNonEmptyBlock( bb ) ) 
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void dispose() 
	{
		chunkLoader.terminate();
		chunkUpdater.terminate();

		synchronized( chunkMap ) 
		{
			for (Iterator<com.badlogic.gdx.utils.IntMap.Entry<Chunk>> it = chunkMap.entries().iterator() ; it.hasNext();) 
			{
				final com.badlogic.gdx.utils.IntMap.Entry<Chunk> chunk = it.next();
				if ( chunk.value != null ) {
					try {
						chunkStorage.unloadChunk( chunk.value );
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				it.remove();
			}
		}		
	}
}