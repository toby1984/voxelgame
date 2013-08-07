package de.codesourcery.voxelgame.core.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.render.BlockRenderer;
import de.codesourcery.voxelgame.core.world.DefaultChunkManager.Hit;

/**
 * A chunk describes a cubic volume of blocks.
 *
 * @author tobias.gierke@voipfuture.com
 */
public final class Chunk 
{
	// number of blocks along X axis	
	public static final int BLOCKS_X = 16; 
	
	// number of blocks along Y axis
	public static final int BLOCKS_Y = 16; 
	
	// number of blocks along Z axis
	public static final int BLOCKS_Z = 16; 
	
	// block width in world coordinates
	public static final float BLOCK_WIDTH = 12f;
	
	// block height in world coordinates
	public static final float BLOCK_HEIGHT = 12f;
	
	// block depth in world coordinates
	public static final float BLOCK_DEPTH = 12f;	
	
	public static final float CHUNK_WIDTH  = BLOCKS_X*BLOCK_WIDTH; // tile width in model coordinates (measured along X axis)
	public static final float CHUNK_HEIGHT = BLOCKS_Y*BLOCK_HEIGHT; // tile height in model cordinates (measured along Y axis)		
	public static final float CHUNK_DEPTH  = BLOCKS_Z*BLOCK_DEPTH; // tile depth in model cordinates (measured along Z axis)	
	
	public static final float HALF_CHUNK_WIDTH = CHUNK_WIDTH/2.0f;
	public static final float HALF_CHUNK_HEIGHT = CHUNK_HEIGHT/2.0f;
	public static final float HALF_CHUNK_DEPTH = CHUNK_DEPTH/2.0f;	
	
	protected static final class ChunkKey 
	{
		public final int chunkX;
		public final int chunkY;
		public final int chunkZ;
		
		public ChunkKey(Chunk chunk) {
			this.chunkX = chunk.x;
			this.chunkY = chunk.y;
			this.chunkZ = chunk.z;
		}
		
		public ChunkKey(int chunkX, int chunkY, int chunkZ) 
		{
			this.chunkX = chunkX;
			this.chunkY = chunkY;
			this.chunkZ = chunkZ;
		}

		@Override
		public int hashCode() {
			int result = 31  + chunkX;
			result = 31 * result + chunkY;
			result = 31 * result + chunkZ;
			return result;
		}

		@Override
		public boolean equals(Object obj) 
		{
			if (this == obj) {
				return true;
			}
			if (obj instanceof ChunkKey ) 
			{
				ChunkKey other = (ChunkKey ) obj;
				return chunkX == other.chunkX && chunkY == other.chunkY &&  chunkZ == other.chunkZ;
			}
			return false;
		}
	}
	
	// FLAGS
	
	/**
	 * Indicates that this chunk's mesh needs to be rebuild.
	 */
	public static final int FLAG_MESH_REBUILD_REQUIRED = 1;
	/**
	 * Indicates that this chunk intersects the current view frustum.
	 */
	public static final int FLAG_VISIBLE= 2;
	
	/**
	 * Indicates that this chunk has been disposed.
	 */
	public static final int FLAG_DISPOSED= 4;	
	
	/**
	 * Indicates this chunk contains only empty blocks.
	 */
	public static final int FLAG_IS_EMPTY = 8;	
	
	/**
	 * Indicates this chunk is pinned in memory and must not be unloaded/disposed.
	 */
	public static final int FLAG_PINNED = 16;		
	
	/**
	 * Indicates data of this chunk has been changed since it was loaded/created.
	 */
	public static final int FLAG_CHANGED_SINCE_LOAD = 32;	
	
	/**
	 * Indicates that lighting of this chunk needs to be recalculated.
	 */
	public static final int FLAG_LIGHT_RECALCULATION_REQUIRED = 64;	
	
	// tile coordinates
	public final int x;
	public final int z; 
	public final int y; 
	
	// AABB of this tile
	private final Vector3 bbMin; 
	private final Vector3 bbMax;
	public  final BoundingBox bb;
	
	public long accessCounter = 0;
	
	public final Block[][][] blocks;
	
	public BlockRenderer blockRenderer;
	
	private final BoundingBox box = new BoundingBox();
	private int flags = FLAG_MESH_REBUILD_REQUIRED|FLAG_LIGHT_RECALCULATION_REQUIRED;
	
	public Chunk(int x, int y,int z) 
	{
		this.x = x;
		this.y = y;
		this.z = z;
		
		blocks = new Block[BLOCKS_X][][];
		for ( int i = 0 ; i < BLOCKS_X;i++) 
		{
			blocks[i] = new Block[BLOCKS_Y][];
			for ( int j = 0 ; j < BLOCKS_Y;j++) {
				Block[] tmp = new Block[BLOCKS_Z];
				blocks[i][j]=tmp;
				for ( int k = 0 ; k < BLOCKS_Z ; k++ ) {
					tmp[k]=new Block();
				}
			}
		}
		
		final float xMin = -HALF_CHUNK_WIDTH+x*CHUNK_WIDTH;
		final float yMin = -HALF_CHUNK_HEIGHT+y*CHUNK_HEIGHT;
		final float zMin = -HALF_CHUNK_DEPTH+z*CHUNK_DEPTH;
		
		final float xMax = HALF_CHUNK_WIDTH+x*CHUNK_WIDTH;
		final float yMax = HALF_CHUNK_HEIGHT+y*CHUNK_HEIGHT;
		final float zMax = HALF_CHUNK_DEPTH+z*CHUNK_DEPTH;		
		
		bbMin = new Vector3(xMin,yMin,zMin);
		bbMax = new Vector3(xMax,yMax,zMax);			
		bb = new BoundingBox( bbMin , bbMax );
	}
	
	public void setBlockType(int blockX,int blockY,int blockZ,IChunkManager chunkManager,byte blockType) 
	{
		blocks[blockX][blockY][blockZ].type=blockType;
		setChangedSinceLoad(true); // mark as dirty so chunk stored on disk will be updated
		setMeshRebuildRequired(true);
		setLightRecalculationRequired(true);
		chunkManager.chunkChanged( this );
		
		System.out.println("Deleted block "+blockX+"/"+blockY+"/"+blockZ+" of "+this);
		
		invalidateAdjacentChunks(blockX, blockY, blockZ, chunkManager);		
	}

	private void invalidateAdjacentChunks(int blockX, int blockY, int blockZ,
			IChunkManager chunkManager)
	{
		// trigger mesh rebuild on adjacent chunks (but only if they're loaded)
		// so that any block faces that might've been hidden and are now 
		// visible are rendered correctly
		if ( blockX == 0 ) {
			Chunk adj = maybeGetLeftNeighbour( chunkManager );
			if ( adj != null ) {
				adj.setMeshRebuildRequired(true);
				System.out.println( "Invalidating left: "+adj);
			}
		} else if ( blockX == Chunk.BLOCKS_X-1 ) {
			Chunk adj = maybeGetRightNeighbour( chunkManager );
			if ( adj != null ) {
				adj.setMeshRebuildRequired(true);
				System.out.println( "Invalidating right: "+adj);
			}
		}
		if ( blockY == 0 ) {
			Chunk adj = maybeGetBottomNeighbour( chunkManager );
			if ( adj != null ) {
				adj.setMeshRebuildRequired(true);
				System.out.println( "Invalidating bottom: "+adj);				
			}
		} else if ( blockY == BLOCKS_Y-1 ) {
			Chunk adj = maybeGetTopNeighbour( chunkManager );
			if ( adj != null ) {
				adj.setMeshRebuildRequired(true);
				System.out.println( "Invalidating top: "+adj);				
			}
		}	
		if ( blockZ == 0 ) {
			Chunk adj = maybeGetFrontNeighbour( chunkManager );
			if ( adj != null ) {
				adj.setMeshRebuildRequired(true);
				System.out.println( "Invalidating front: "+adj);				
			}
		} else if ( blockY == BLOCKS_Z-1 ) {
			Chunk adj = maybeGetBackNeighbour( chunkManager );
			if ( adj != null ) {
				adj.setMeshRebuildRequired(true);
				System.out.println( "Invalidating back: "+adj);				
			}
		}
	}
	
	public Chunk maybeGetLeftNeighbour(IChunkManager manager) {
		return manager.maybeGetChunk( this.x - 1 , this.y , this. z );
	}
	
	public Chunk maybeGetRightNeighbour(IChunkManager manager) {
		return manager.maybeGetChunk( this.x + 1 , this.y , this. z );
	}	
	
	public Chunk maybeGetTopNeighbour(IChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y +1  , this. z );
	}
	
	public Chunk maybeGetBottomNeighbour(IChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y - 1  , this. z );
	}	
	
	public Chunk maybeGetFrontNeighbour(IChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y   , this. z +1 );
	}	
	
	public Chunk maybeGetBackNeighbour(IChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y   , this. z -1 );
	}	
	
	public Chunk getLeftNeighbour(IChunkManager manager) {
		return manager.getChunk( this.x - 1 , this.y , this. z );
	}
	
	public Chunk getRightNeighbour(IChunkManager manager) {
		return manager.getChunk( this.x + 1 , this.y , this. z );
	}	
	
	public Chunk getTopNeighbour(IChunkManager manager) {
		return manager.getChunk( this.x , this.y +1  , this. z );
	}
	
	public Chunk getBottomNeighbour(IChunkManager manager) {
		return manager.getChunk( this.x , this.y - 1  , this. z );
	}	
	
	public Chunk getFrontNeighbour(IChunkManager manager) {
		return manager.getChunk( this.x , this.y   , this. z +1 );
	}	
	
	public Chunk getBackNeighbour(IChunkManager manager) {
		return manager.getChunk( this.x , this.y   , this. z -1 );
	}	
	
	public Vector3 getCenter() {
		return bb.getCenter();
	}
	
	public boolean containsPoint(Vector3 v) {
		return bb.contains( v );
	}
	
	public boolean intersects(Ray ray) 
	{
		return Intersector.intersectRayBoundsFast( ray , bb );
	}
	
	/**
	 * Returns the x/y/z coordinates of the non-empty block
	 * closest to a ray's origin.
	 * 
	 * @param ray
	 * @param result
	 * @return
	 */
	public boolean getClosestIntersection(Ray ray,Vector3 result,Vector3 hitPointOnBlock) 
	{
		float distanceToHitSquared = 0;
		boolean hit = false;
		final Vector3 closestHitpointOnBlock = new Vector3();
		
		for ( int x = 0 ; x < BLOCKS_X ; x++ ) 
		{
			for ( int y = 0 ; y < BLOCKS_Y ; y++ ) 
			{
				for ( int z = 0 ; z < BLOCKS_Z ; z++ ) 
				{
					if ( blocks[x][y][z].isAirBlock() ) { // do not intersect with empty blocks
						continue;
					}
					
					populateBlockBoundingBox( x , y, z , box );
					
					if ( Intersector.intersectRayBounds(ray, box,hitPointOnBlock) ) 
					{
						float distance = hitPointOnBlock.dst2( ray.origin );
						if ( ! hit || distance < distanceToHitSquared ) 
						{
							hit = true;
							closestHitpointOnBlock.set( hitPointOnBlock );
							result.x = x;
							result.y = y;
							result.z = z;
							distanceToHitSquared = distance;
						} 
					}
				}
			}
		}
		if ( hit ) {
			hitPointOnBlock.set( closestHitpointOnBlock );
		}
		return hit;
	}
	
	public void printBoundingBoxes() {
		
		BoundingBox bb = new BoundingBox();
		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 
		{
			for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 
			{
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 
				{
					populateBlockBoundingBox( x , y , z , bb );
					System.out.println("Block("+x+","+y+","+z+") = "+bb+" (size: "+bb.getDimensions()+")");
				}
			}
		}
	}
	
	private void populateBlockBoundingBox(int blockX,int blockY,int blockZ,BoundingBox box) 
	{
		final float xOrig = bb.min.x; 
		final float yOrig = bb.min.y; 
		final float zOrig = bb.min.z; 
		
		float x1 = xOrig + blockX * BLOCK_WIDTH;
		float y1 = yOrig + blockY * BLOCK_HEIGHT;
		float z1 = zOrig + blockZ * BLOCK_DEPTH;
		
		box.min.set(x1,y1,z1);
		box.max.set(x1+BLOCK_WIDTH,y1+BLOCK_HEIGHT,z1+BLOCK_DEPTH);
		box.set(box.min,box.max);		
	}
	
	@Override
	public String toString()
	{
	    return "Chunk ("+x+","+y+","+z+" , "+flagsToString()+" )";
	}
	
	public final void dispose() 
	{
		if ( ! isDisposed() ) 
		{
			if ( blockRenderer != null ) {
				blockRenderer.dispose();
				blockRenderer = null;
			}
			setFlag(FLAG_DISPOSED,true);
		}
	}

	public boolean getBlockContaining(Vector3 worldCoords, Hit hit) 
	{
		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 
		{
			for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 
			{
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 
				{
					populateBlockBoundingBox( x , y , z , box );
					if ( box.contains( worldCoords ) ) 
					{
						hit.blockX = x;
						hit.blockY = y;
						hit.blockZ = z;
						return true;
					}
				}
			}
		}		
		return false;
	}

	public boolean intersectsNonEmptyBlock(BoundingBox toTest) 
	{
		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 
		{
			for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 
			{
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 
				{
					if ( ! blocks[x][y][z].isAirBlock() ) 
					{
						populateBlockBoundingBox( x , y , z , box );
						if ( intersects(toTest, box ) )
						{
							return true;
						}
					}
				}
			}
		}		
		return false;
	}
	
	public static boolean intersects(BoundingBox b1,BoundingBox b2) 
	{
		if( b1.min.x >= b2.min.x && b1.max.x <= b2.max.x &&
				b1.min.y >= b2.min.y && b1.max.y <= b2.max.y &&
				b1.min.z >= b2.min.z && b1.max.z <= b2.max.z )
		{
			return true;
		}

		if( b2.max.x < b1.min.x || b2.min.x > b1.max.x ) {
			return false;
		}
		if( b2.max.y < b1.min.y || b2.min.y > b1.max.y ) {
			return false;
		}
		if( b2.max.z < b1.min.z || b2.min.z > b1.max.z ) {
			return false;
		}
		return true;
	}
	
	public boolean isVisible() {
		return isFlagSet( FLAG_VISIBLE );
	}
	
	public void setVisible(boolean visible) {
		setFlag(FLAG_VISIBLE , visible );
	}
	
	public boolean isInvisible() 
	{
		return ( this.flags & FLAG_VISIBLE ) == 0; 
	}	
	
	public boolean isMeshRebuildRequired() {
		return isFlagSet( FLAG_MESH_REBUILD_REQUIRED); 
	}
	
	public void setMeshRebuildRequired(boolean rebuildRequired) 
	{
		if ( rebuildRequired ) {
			System.out.println("Requesting mesh rebuild for "+this);
		}
		setFlag(FLAG_MESH_REBUILD_REQUIRED , rebuildRequired );
	}
	
	public boolean isLightRecalculationRequired() {
		return isFlagSet( FLAG_LIGHT_RECALCULATION_REQUIRED); 
	}
	
	public void setLightRecalculationRequired(boolean rebuildRequired) 
	{
		if ( rebuildRequired ) {
			System.out.println("Requesting lighing for "+this);
		}
		setFlag(FLAG_LIGHT_RECALCULATION_REQUIRED , rebuildRequired );
	}	

	public boolean isDisposed() {
		return isFlagSet(FLAG_DISPOSED);
	}
	
	public void setEmpty(boolean isEmpty) {
		setFlag(FLAG_IS_EMPTY,isEmpty);
	}
	
	public boolean isEmpty() {
		return isFlagSet(FLAG_IS_EMPTY );
	}
	
	public void setFlags(int mask) {
		this.flags |= mask;
	}
	
	public void setChangedSinceLoad(boolean isChanged) {
		setFlag(FLAG_CHANGED_SINCE_LOAD,isChanged);
	}
	
	public boolean hasChangedSinceLoad() {
		return isFlagSet(FLAG_CHANGED_SINCE_LOAD);
	}		
	
	public void setPinned(boolean isPinned) {
		setFlag(FLAG_PINNED,isPinned);
	}
	
	public boolean isPinned() {
		return isFlagSet(FLAG_PINNED);
	}	
	
	public boolean isNotPinned() 
	{
		return ( this.flags & FLAG_PINNED) == 0; 
	}		
	
	private void setFlag(int mask,boolean enable) 
	{
		if ( enable ) {
			flags |= mask;
		} else {
			flags &= ~mask;
		}
	}	
	
	private boolean isFlagSet(int mask) { return ( flags & mask ) != 0;  }
	
	private String flagsToString() 
	{
		final StringBuilder result = new StringBuilder();
		if ( isFlagSet( FLAG_MESH_REBUILD_REQUIRED ) ) {
			result.append("REBUILD_REQUIRED | ");
		}
		if ( isFlagSet( FLAG_LIGHT_RECALCULATION_REQUIRED) ) {
			result.append("LIGHTING_RECALC | ");
		}	
		if ( isFlagSet( FLAG_CHANGED_SINCE_LOAD) ) {
			result.append("NEEDS_STORE | ");
		}			
		if ( isFlagSet( FLAG_VISIBLE ) ) {
			result.append("VISIBLE | ");
		}
		if ( isFlagSet( FLAG_DISPOSED ) ) {
			result.append("DISPOSED | ");
		}
		if ( isFlagSet( FLAG_IS_EMPTY) ) {
			result.append("EMPTY | ");
		}
		if ( isFlagSet( FLAG_PINNED) ) {
			result.append("PINNED| ");
		}		
		return result.toString();
	}	
}