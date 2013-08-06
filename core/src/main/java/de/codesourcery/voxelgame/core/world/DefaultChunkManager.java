package de.codesourcery.voxelgame.core.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.world.Chunk.ChunkKey;
import de.codesourcery.voxelgame.core.world.ChunkList.ChunkListEntry;


public class DefaultChunkManager implements IChunkManager 
{
	// the current chunk (3x3 chunks)
	public static final int MAX_CACHED_CHUNKS = 90;
	
	private final Vector3 TMP = new Vector3();
	
	private final ChunkList chunkList = new ChunkList();
	
	private final Map<ChunkKey,Chunk> chunkMap = new HashMap<>();
	private final List<Chunk> visibleChunks = new ArrayList<>();

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

	private boolean reloadWholeChunk= true;
	
	private long accessCounter;

	public DefaultChunkManager(Camera camera) 
	{
		this.camera = camera;
		cameraMoved();
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.voxelgame.core.world.IChunkManager#getCurrentChunk()
	 */
	@Override
	public List<Chunk> getVisibleChunks() 
	{
		accessCounter++;		
		if ( reloadWholeChunk ) 
		{
			reloadChunks();
			reloadWholeChunk = false;
		}
		return visibleChunks;
	}

	private void reloadChunks() 
	{
		System.out.println("Reloading chunks around: "+cameraChunkX+" / "+cameraChunkY+" / "+cameraChunkZ);
		
		for ( Chunk chunk : chunkMap.values() ) {
			chunk.setPinned(false);
		}
		
		for ( int deltaX = -3 ; deltaX <= 3 ; deltaX++ ) {
			for ( int deltaZ = -3 ; deltaZ <= 3 ; deltaZ++ ) {
				getChunk( cameraChunkX + deltaX , 0 , cameraChunkZ+deltaZ ).setFlags(Chunk.FLAG_PINNED);
			}			
		}
		
		updateVisibleChunksList();
	}
	
	@Override
	public Chunk maybeGetChunk(int chunkX, int chunkY, int chunkZ) 
	{
		final ChunkKey key = new ChunkKey(chunkX,chunkY,chunkZ);
		Chunk newChunk = chunkMap.get( key );
		if ( newChunk != null ) 
		{
			newChunk.accessCounter = accessCounter;
			return newChunk;
		}
		return null;
	}
	
	@Override
	public Chunk getChunk(int chunkX, int chunkY, int chunkZ) 
	{		
		final ChunkKey key = new ChunkKey(chunkX,chunkY,chunkZ);
		Chunk newChunk = chunkMap.get( key );
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
		
//		System.out.println("*** Loaded chunk "+newChunk);
		
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
			
			if ( toRemove != null ) {
				Chunk chunkToRemove = toRemove.chunk;

				//			System.out.println("*** Disposing chunk "+chunkToRemove+" with access count "+chunkToRemove.accessCounter);

				chunkList.remove( toRemove , toRemovePrevious );
				chunkMap.remove( new ChunkKey( chunkToRemove ) );
				visibleChunks.remove( chunkToRemove );
				try {
					chunkStorage.unloadChunk( chunkToRemove );
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				chunkToRemove.dispose();
			} else {
				System.err.println("Failed to remove cached chunk, cache size is now: "+chunkMap.size());
			}
		}
		
		chunkMap.put( key , newChunk );
		chunkList.add( newChunk );
		return newChunk;
	}	
	
	private void updateVisibleChunksList() 
	{
		visibleChunks.clear();
		
		ChunkListEntry current = chunkList.head;
		while( current != null ) 
		{
			final Chunk chunk=current.chunk;
			final boolean isVisible = ! chunk.isEmpty() && camera.frustum.boundsInFrustum( chunk.bb );
			chunk.setVisible( isVisible );
			if ( isVisible ) 
			{
				visibleChunks.add( chunk );
			} 
			current = current.next;
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
			// camera still on same chunk
			updateVisibleChunksList();
			return;
		}
		
		System.out.println("*** CAMERA: Now at chunk ( "+this.cameraChunkX+","+this.cameraChunkY+","+this.cameraChunkZ+") ***");
		this.cameraChunkX = newCameraChunkX;
		this.cameraChunkY = newCameraChunkY;
		this.cameraChunkZ = newCameraChunkZ;		
		
		reloadWholeChunk=true;		
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

		for ( Chunk chunk : getVisibleChunks() ) 
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
		for ( Chunk chunk : getVisibleChunks() ) 
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
		for ( Chunk chunk : getVisibleChunks() ) 
		{
			if ( chunk.intersectsNonEmptyBlock( bb ) ) 
			{
				return true;
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
		for (Iterator<Entry<ChunkKey, Chunk>> it = chunkMap.entrySet() .iterator(); it.hasNext();) 
		{
			final Entry<ChunkKey, Chunk> chunk = it.next();
			chunk.getValue().dispose();
			it.remove();
		}
		visibleChunks.clear();
	}
}