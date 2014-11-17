package de.codesourcery.voxelgame.core.world;

import java.util.concurrent.atomic.AtomicInteger;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Pool.Poolable;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.render.BlockRenderer;
import de.codesourcery.voxelgame.core.world.ChunkManager.Hit;

/**
 * A chunk describes a cubic volume of blocks.
 *
 * @author tobias.gierke@voipfuture.com
 */
public final class Chunk implements Poolable
{
	// number of blocks along X axis
	public static final int BLOCKS_X = 16;

	// number of blocks along Y axis
	public static final int BLOCKS_Y = 256;

	// number of blocks along Z axis
	public static final int BLOCKS_Z = 16;

	// block width in world coordinates
	public static final float BLOCK_WIDTH = 44f;

	// block height in world coordinates
	public static final float BLOCK_HEIGHT = 44f;

	// block depth in world coordinates
	public static final float BLOCK_DEPTH = 44f;

	public static final float CHUNK_WIDTH  = BLOCKS_X*BLOCK_WIDTH; // tile width in model coordinates (measured along X axis)
	public static final float CHUNK_HEIGHT = BLOCKS_Y*BLOCK_HEIGHT; // tile height in model cordinates (measured along Y axis)
	public static final float CHUNK_DEPTH  = BLOCKS_Z*BLOCK_DEPTH; // tile depth in model cordinates (measured along Z axis)

	public static final float HALF_CHUNK_WIDTH = CHUNK_WIDTH/2.0f;
	public static final float HALF_CHUNK_HEIGHT = CHUNK_HEIGHT/2.0f;
	public static final float HALF_CHUNK_DEPTH = CHUNK_DEPTH/2.0f;

	// FLAGS
	public static final class ChunkKey
	{
		public final int x;
		public final int y;
		public final int z;
		public final int hashCode;

		public ChunkKey(int x, int y, int z)
		{
			this.x = x;
			this.y = y;
			this.z = z;

			int result = 31  + x;
			result = 31 * result + y;
			result = 31 * result + z;
			hashCode = result;
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj)
		{
			if ( obj instanceof ChunkKey)
			{
				final ChunkKey other = (ChunkKey) obj;
				return this.x == other.x && this.y == other.y && this.z == other.z;
			}
			return false;
		}

		public static void populateBoundingBox(int x,int y,int z,BoundingBox boundingBox)
		{
			final float xMin = -HALF_CHUNK_WIDTH+x*CHUNK_WIDTH;
			final float yMin = -HALF_CHUNK_HEIGHT+y*CHUNK_HEIGHT;
			final float zMin = -HALF_CHUNK_DEPTH+z*CHUNK_DEPTH;

			final float xMax = HALF_CHUNK_WIDTH+x*CHUNK_WIDTH;
			final float yMax = HALF_CHUNK_HEIGHT+y*CHUNK_HEIGHT;
			final float zMax = HALF_CHUNK_DEPTH+z*CHUNK_DEPTH;

			boundingBox.min.set(xMin,yMin,zMin);
			boundingBox.max.set(xMax,yMax,zMax);
			boundingBox.set( boundingBox.min , boundingBox.max );
		}

		public void populateCenter(Vector3 center)
		{
			final float xMin = -HALF_CHUNK_WIDTH+x*CHUNK_WIDTH;
			final float yMin = -HALF_CHUNK_HEIGHT+y*CHUNK_HEIGHT;
			final float zMin = -HALF_CHUNK_DEPTH+z*CHUNK_DEPTH;

			final float xMax = HALF_CHUNK_WIDTH+x*CHUNK_WIDTH;
			final float yMax = HALF_CHUNK_HEIGHT+y*CHUNK_HEIGHT;
			final float zMax = HALF_CHUNK_DEPTH+z*CHUNK_DEPTH;

			center.x = (xMin+xMax) / 2.0f;
			center.y = (yMin+yMax) / 2.0f;
			center.z = (zMin+zMax) / 2.0f;
		}
	}

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
	 * Indicates data of this chunk has been changed since it was loaded/created.
	 */
	public static final int FLAG_CHANGED_SINCE_LOAD = 32;

	/* *********************
	 * Make sure to reset any fields you add here ( see initialize(int,int,int) )
	 * *********************
	 */

	// chunk coordinates
	public int x;
	public int z;
	public int y;

	// AABB of this tile
	public final BoundingBox boundingBox = new BoundingBox();

	// used as temporary storage
	private final BoundingBox TMP_BB = new BoundingBox();

	public long accessCounter = 0;

	public final byte[] blockType;
	public final byte[] lightLevel;

	public final BlockRenderer blockRenderer;
	public int renderedBlockCount=0; // TODO: Remove debug code

	// hint: Need to use CAS here because using synchronize(this) causes random dead-locks
	// because of ChunkRenderer#determineSidesToRender() accessing adjacent chunks
	// in a random order and thus causing inconsistent lock order
	private final AtomicInteger flags = new AtomicInteger( FLAG_MESH_REBUILD_REQUIRED );

	private int hashCode;

	public Chunk(int x,int y,int z)
	{
		this.blockRenderer = new BlockRenderer();

		final int arraySize = BLOCKS_X*BLOCKS_Y*BLOCKS_Z;
		lightLevel = new byte[ arraySize ];
		blockType = new byte[arraySize];
		initialize(x,y,z);
	}

	@Override
	public boolean equals(Object obj)
	{
		if ( obj instanceof Chunk)
		{
			final Chunk other = (Chunk) obj;
			return this.hashCode == other.hashCode && other.x == x && other.y == y && other.z == z;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	public Chunk()
	{
		this.blockRenderer = new BlockRenderer();
		final int arraySize = BLOCKS_X*BLOCKS_Y*BLOCKS_Z;
		lightLevel = new byte[ arraySize ];
		blockType = new byte[arraySize];
	}

	public void initialize(int x, int y,int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;

		ChunkKey.populateBoundingBox( x,y,z, this.boundingBox );

		flags.set( FLAG_MESH_REBUILD_REQUIRED );
		accessCounter = 0;
		this.hashCode = Chunk.calcChunkKey(x,y,z);
	}

	public static int calcChunkKey(int chunkX,int chunkY,int chunkZ)
	{
		int result = 31  + chunkX;
		result = 31 * result + chunkY;
		result = 31 * result + chunkZ;
		return result;
	}

	public static int calcChunkKey(Chunk chunk)
	{
		int result = 31  + chunk.x;
		result = 31 * result + chunk.y;
		result = 31 * result + chunk.z;
		return result;
	}

	public void setBlockType(int blockX,int blockY,int blockZ,ChunkManager chunkManager,byte newType)
	{
		synchronized(this)
		{
			System.out.println("Changed type of block "+blockX+"/"+blockY+"/"+blockZ+" of "+this+" to new type "+newType);
			blockType[blockX+BLOCKS_X*blockY+(BLOCKS_X*BLOCKS_Y)*blockZ]=newType;
			setChangedSinceLoad(true); // mark as dirty so chunk stored on disk will be updated
			setMeshRebuildRequired(true);
		}
		invalidateAdjacentChunks(blockX, blockY, blockZ, chunkManager);
		chunkManager.chunkChanged( this );
	}

	private void invalidateAdjacentChunks(int blockX, int blockY, int blockZ,ChunkManager chunkManager)
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

	public Chunk maybeGetLeftNeighbour(ChunkManager manager) {
		return manager.maybeGetChunk( this.x - 1 , this.y , this. z );
	}

	public Chunk maybeGetRightNeighbour(ChunkManager manager) {
		return manager.maybeGetChunk( this.x + 1 , this.y , this. z );
	}

	public Chunk maybeGetTopNeighbour(ChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y +1  , this. z );
	}

	public Chunk maybeGetBottomNeighbour(ChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y - 1  , this. z );
	}

	public Chunk maybeGetFrontNeighbour(ChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y   , this. z +1 );
	}

	public Chunk maybeGetBackNeighbour(ChunkManager manager) {
		return manager.maybeGetChunk( this.x , this.y   , this. z -1 );
	}

	public Chunk getLeftNeighbour(ChunkManager manager) {
		return manager.getChunk( this.x - 1 , this.y , this. z );
	}

	public Chunk getRightNeighbour(ChunkManager manager) {
		return manager.getChunk( this.x + 1 , this.y , this. z );
	}

	public Chunk getTopNeighbour(ChunkManager manager) {
		return manager.getChunk( this.x , this.y +1  , this. z );
	}

	public Chunk getBottomNeighbour(ChunkManager manager) {
		return manager.getChunk( this.x , this.y - 1  , this. z );
	}

	public Chunk getFrontNeighbour(ChunkManager manager) {
		return manager.getChunk( this.x , this.y   , this. z +1 );
	}

	public Chunk getBackNeighbour(ChunkManager manager) {
		return manager.getChunk( this.x , this.y   , this. z -1 );
	}

	public Vector3 getCenter() {
		return boundingBox.getCenter();
	}

	public boolean containsPoint(Vector3 v) {
		return boundingBox.contains( v );
	}

	public boolean intersects(Ray ray)
	{
		return Intersector.intersectRayBoundsFast( ray , boundingBox );
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
					if ( Block.isAirBlock( blockType[x+BLOCKS_X*y+(BLOCKS_X*BLOCKS_Y)*z] ) ) { // do not intersect with empty blocks
						continue;
					}

					populateBlockBoundingBox( x , y, z , TMP_BB );

					if ( Intersector.intersectRayBounds(ray, TMP_BB,hitPointOnBlock) )
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
		final float xOrig = boundingBox.min.x;
		final float yOrig = boundingBox.min.y;
		final float zOrig = boundingBox.min.z;

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

	/**
	 * Disposes any resources associated with this chunk.
	 *
	 * <p>This method <b>must</b> be called from the
	 * OpenGL rendering thread if the chunk's block renderer has an active
	 * VBO assigned.
	 */
	public final void dispose()
	{
		blockRenderer.dispose();
		renderedBlockCount = 0;
		setFlag(FLAG_DISPOSED,true);
	}

	public boolean getBlockContaining(Vector3 worldCoords, Hit hit)
	{
		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ )
		{
			for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ )
			{
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ )
				{
					populateBlockBoundingBox( x , y , z , TMP_BB );
					if ( TMP_BB.contains( worldCoords ) )
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
					if ( Block.isNoAirBlock( blockType[x+BLOCKS_X*y+(BLOCKS_X*BLOCKS_Y)*z] ) )
					{
						populateBlockBoundingBox( x , y , z , TMP_BB );
						if ( intersects(toTest, TMP_BB ) )
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
		return ( this.flags.get() & FLAG_VISIBLE ) == 0;
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

	public boolean isDisposed() {
		return isFlagSet(FLAG_DISPOSED);
	}

	public boolean isNotDisposed()
	{
		return (flags.get() & FLAG_DISPOSED) == 0;
	}

	public void setEmpty(boolean isEmpty) {
		setFlag(FLAG_IS_EMPTY,isEmpty);
	}

	public boolean isEmpty() {
		return isFlagSet(FLAG_IS_EMPTY );
	}

	public void setChangedSinceLoad(boolean isChanged) {
		setFlag(FLAG_CHANGED_SINCE_LOAD,isChanged);
	}

	public boolean hasChangedSinceLoad() {
		return isFlagSet(FLAG_CHANGED_SINCE_LOAD);
	}

	private void setFlag(int mask,boolean enable)
	{
		int currentValue = this.flags.get();
		int newValue = enable ? currentValue | mask : currentValue & ~mask;
		while ( ! this.flags.compareAndSet( currentValue , newValue ) )
		{
			currentValue = this.flags.get();
			newValue = enable ? currentValue | mask : currentValue & ~mask;
		}
	}

	private boolean isFlagSet(int mask)
	{
		return ( flags.get() & mask ) != 0;
	}

	private String flagsToString()
	{
		final StringBuilder result = new StringBuilder();
		if ( isFlagSet( FLAG_MESH_REBUILD_REQUIRED ) ) {
			result.append("REBUILD_REQUIRED | ");
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
		return result.toString();
	}

	@Override
	public void reset() {
		dispose();
	}
}