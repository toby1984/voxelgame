package de.codesourcery.voxelgame.core.world;

import com.badlogic.gdx.utils.Pool;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.Constants;

public class ChunkFactory
{
	private final NoiseGenerator rnd = new NoiseGenerator(Chunk.BLOCKS_X, Chunk.BLOCKS_Y,Chunk.BLOCKS_Z,0xdeadbeef );
	
	private final Pool<Chunk> pool = new Pool<Chunk>(10) {

		@Override
		protected Chunk newObject() {
			return new Chunk();
		}		
	};
	
	public ChunkFactory(long seed) 
	{
		rnd.setSeed( seed );
	}

	public Chunk createChunk(int x,int y,int z) 
	{
		final Chunk result = getChunkFromPool(x,y,z);
		initialize3D(result);
		return result;
	}
	
	public Chunk getChunkFromPool(int chunkX,int chunkY,int chunkZ) 
	{
		if ( Constants.USE_CHUNK_POOL ) {
			Chunk result = null;
			synchronized(pool) {
				result = pool.obtain();
			}
			result.initialize( chunkX,chunkY,chunkZ);
			return result;			
		}
		return new Chunk(chunkX,chunkY,chunkZ);
	}
	
	public void returnChunkToPool(Chunk chunk) 
	{
		if ( Constants.USE_CHUNK_POOL ) {
			synchronized(pool) {
				pool.free( chunk );
			}			
		} else {
			chunk.dispose();
		}
	}	
	
	public Chunk createEmptyChunk(int x,int y,int z) 
	{
		final Chunk result = getChunkFromPool(x,y,z);
		result.setEmpty( true );
		return result;
	}
	
	public Chunk createSolidChunk(int x,int y,int z) 
	{
		final Chunk result = getChunkFromPool(x,y,z);
		for ( int xx = 0 ; xx < Chunk.BLOCKS_X ; xx++ ) 
		{
			for ( int zz = 0 ; zz < Chunk.BLOCKS_Z ; zz++ ) 
			{	
				for ( int yy = 0 ; yy < Chunk.BLOCKS_Y ; yy++ ) 
				{
					result.blockType[xx+Chunk.BLOCKS_X*yy+(Chunk.BLOCKS_X*Chunk.BLOCKS_Y)*zz] = Block.Type.SOLID;
				}
			}
		}
		return result;
	}	
	
	private void initialize2D(Chunk chunk) 
	{
		final float chunkSize = 0.3f;
		final float[] heightMap = rnd.createNoise2D( chunk.x*chunkSize , chunk.z*chunkSize , chunkSize  , 7 , 4f );
		
		final byte[] blockTypes = chunk.blockType;
		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 
		{
			for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 
			{	
				float height = heightMap[x+z*Chunk.BLOCKS_Z]*0.9f;
				if ( height < 0.1f ) {
					height = 0.1f;
				}
				float scale = 1.0f/(float) Chunk.BLOCKS_Y;
				
				for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 
				{
					float currentHeight = y * scale;
					final int currentIndex = x+Chunk.BLOCKS_X*y+(Chunk.BLOCKS_X*Chunk.BLOCKS_Y)*z;
					if ( currentHeight < height ) 
					{
						if ( height < 0.3f ) {
							blockTypes[currentIndex] = Block.Type.WATER;
						} else {
							blockTypes[currentIndex] = Block.Type.SOLID;
						}
					} else {
						blockTypes[currentIndex]  = Block.Type.AIR;
					}
				}
			}
		}
	}
	
	private void initialize3D(Chunk chunk) 
	{
		final float chunkSize = 0.3f;
		final float[] heightMap = rnd.createNoise3D( chunk.x*chunkSize , chunk.y*chunkSize, chunk.z*chunkSize  , chunkSize  , 5 , 4f );
		
		final float factor = 1.0f / (float) (Chunk.BLOCKS_Y*3f);
		final byte[] blockTypes = chunk.blockType;
		
		for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 		
		{
			for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 			
			{	
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 				
				{
					float height = heightMap[z+x*Chunk.BLOCKS_Z+y*(Chunk.BLOCKS_X*Chunk.BLOCKS_Z)]*(1.0f-y*factor);
					final int currentIndex = x+Chunk.BLOCKS_X*y+(Chunk.BLOCKS_X*Chunk.BLOCKS_Y)*z;
					if ( y == 0 || ( height >= 0.5f && height <= 0.7f) ) 
					{
						blockTypes[currentIndex ] = Block.Type.SOLID;
					} else {
						blockTypes[currentIndex ] = Block.Type.AIR;
					}
				}
			}
		}
	}	
}