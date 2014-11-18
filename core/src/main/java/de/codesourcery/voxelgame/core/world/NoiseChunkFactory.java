package de.codesourcery.voxelgame.core.world;

import de.codesourcery.voxelgame.core.Block;

public class NoiseChunkFactory extends AbstractChunkFactory
{
	private final NoiseGenerator rnd = new NoiseGenerator(Chunk.BLOCKS_X, Chunk.BLOCKS_Y,Chunk.BLOCKS_Z,0xdeadbeef );

	public NoiseChunkFactory(long seed)
	{
		rnd.setSeed( seed );
	}

	@Override
	protected void initializeChunk(Chunk chunk)
	{
		final float chunkSize = 0.3f;
		final float[] heightMap = rnd.createNoise3D( chunk.x*chunkSize , chunk.y*chunkSize, chunk.z*chunkSize  , chunkSize  , 5 , 4f );

		final float factor = 1.0f / (Chunk.BLOCKS_Y*3f);
		final byte[] blockTypes = chunk.blockType;

		boolean hasOnlyAirBlocks = true;
		for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ )
		{
			for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ )
			{
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ )
				{
					final float height = heightMap[z+x*Chunk.BLOCKS_Z+y*(Chunk.BLOCKS_X*Chunk.BLOCKS_Z)]*(1.0f-y*factor);
					final int currentIndex = x+Chunk.BLOCKS_X*y+(Chunk.BLOCKS_X*Chunk.BLOCKS_Y)*z;
					// ( y == 0 ) ==> create solid ground
					// if ( y == 0 || ( height >= 0.5f && height <= 0.7f) )
				    if ( ( height >= 0.5f && height <= 0.7f) )
					{
						blockTypes[currentIndex ] = Block.Type.SOLID;
						hasOnlyAirBlocks = false;
					} else {
						blockTypes[currentIndex ] = Block.Type.AIR;
					}
				}
			}
		}
		chunk.setEmpty( hasOnlyAirBlocks );
	}
}