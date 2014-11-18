package de.codesourcery.voxelgame.core.world;

import de.codesourcery.voxelgame.core.Block;

public class DebugChunkFactory extends AbstractChunkFactory {

	@Override
	protected void initializeChunk(Chunk chunk)
	{
		final byte[] blockTypes = chunk.blockType;
		for ( int x = 5 ; x < 10 ; x++ ) {
			for ( int y = 5 ; y < 10 ; y++ ) {
				for ( int z = 5 ; z < 10 ; z++ ) {
					final int currentIndex = x+Chunk.BLOCKS_X*y+(Chunk.BLOCKS_X*Chunk.BLOCKS_Y)*z;
					blockTypes[ currentIndex ] = Block.Type.SOLID;
				}
			}
		}
		chunk.setEmpty(false);
	}

}
