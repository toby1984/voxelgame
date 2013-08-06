package de.codesourcery.voxelgame.core.world;

import de.codesourcery.voxelgame.core.Block;

public class ChunkFactory
{
	private final DefaultNoiseGenerator rnd = new DefaultNoiseGenerator(Chunk.BLOCKS_X, Chunk.BLOCKS_Y,Chunk.BLOCKS_Z,0xdeadbeef );
	
	public ChunkFactory(long seed) 
	{
		rnd.setSeed( seed );
	}

	public Chunk createChunk(int x,int y,int z) 
	{
		final Chunk result = new Chunk(x,y,z);
		initialize3D(result);
		return result;
	}
	
	public Chunk createEmptyChunk(int x,int y,int z) 
	{
		final Chunk result = new Chunk(x,y,z);
		result.setEmpty( true );
		return result;
	}
	
	public Chunk createSolidChunk(int x,int y,int z) 
	{
		final Chunk result = new Chunk(x,y,z);
		for ( int xx = 0 ; xx < Chunk.BLOCKS_X ; xx++ ) 
		{
			for ( int zz = 0 ; zz < Chunk.BLOCKS_Z ; zz++ ) 
			{	
				for ( int yy = 0 ; yy < Chunk.BLOCKS_Y ; yy++ ) 
				{
					result.blocks[xx][yy][zz].type = Block.Type.SOLID;
				}
			}
		}
		return result;
	}	
	
	private void initialize2D(Chunk chunk) 
	{
		final float chunkSize = 0.3f;
		final float[] heightMap = rnd.createNoise2D( chunk.x*chunkSize , chunk.z*chunkSize , chunkSize  , 7 , 4f );
		
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
					Block block = chunk.blocks[x][y][z];
					if ( currentHeight < height ) 
					{
						if ( height < 0.3f ) {
							block.type = Block.Type.WATER;
						} else {
							block.type = Block.Type.SOLID;
						}
					} else {
						block.type = Block.Type.AIR;
					}
				}
			}
		}
	}
	
	private void initialize3D(Chunk chunk) 
	{
		final float chunkSize = 0.3f;
		final float[] heightMap = rnd.createNoise3D( chunk.x*chunkSize , chunk.y*chunkSize, chunk.z*chunkSize  , chunkSize  , 5 , 4f );
		
		for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 		
		{
			for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 			
			{	
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 				
				{
					float height = heightMap[z+x*Chunk.BLOCKS_Z+y*(Chunk.BLOCKS_X*Chunk.BLOCKS_Z)];
					Block block = chunk.blocks[x][y][z];
					if ( y == 0 || ( height >= 0.5f && height <= 0.7f) ) 
					{
						block.type = Block.Type.SOLID;
					} else {
						block.type = Block.Type.AIR;
					}
				}
			}
		}
	}	
}