package de.codesourcery.voxelgame.core.world;

import java.util.Random;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.world.Chunk.ChunkKey;

public class DebugChunkFactory extends AbstractChunkFactory {

//	@Override
//	protected void initializeChunk(Chunk chunk)
//	{
//		final byte[] blockTypes = chunk.blockType;
//		for ( int x = 5 ; x < 10 ; x++ ) {
//			for ( int y = 5 ; y < 10 ; y++ ) {
//				for ( int z = 5 ; z < 10 ; z++ ) {
//					final int currentIndex = x+Chunk.BLOCKS_X*y+(Chunk.BLOCKS_X*Chunk.BLOCKS_Y)*z;
//					blockTypes[ currentIndex ] = Block.Type.SOLID;
//				}
//			}
//		}
//		chunk.setEmpty(false);
//	}

	@Override
	protected void initializeChunk(Chunk chunk)
	{

		final int CHARGES_PER_CHUNK = 3;

		final float MAX_CHARGE_STRENGTH = 900;

		final Random rnd = new Random();

		// calculate position of three random charges in each chunk
		// = 9 * 3 = 27 charges
		final Vector3[] chargePositions = new Vector3[ 3*3*3*CHARGES_PER_CHUNK ];
		final float[] chargeStrength = new float[3*3*3*CHARGES_PER_CHUNK];

		final int xEnd = chunk.x+1;
		final int yEnd = chunk.y+1;
		final int zEnd = chunk.z+1;

		final BoundingBox bb = new BoundingBox();
		for ( int i=0, x = chunk.x -1  ; x <= xEnd ; x++ )
		{
			for ( int y = chunk.y -1  ; y <= yEnd ; y++ )
			{
				for ( int z = chunk.z -1  ; z <= zEnd ; z++ )
				{
					ChunkKey.populateBoundingBox(x, y, z, bb);

					final long hash = 31*(31*((31*x)+y)+z);
					rnd.setSeed( hash );

					chargePositions[i] = new Vector3(
					bb.min.x + rnd.nextFloat()*(Chunk.CHUNK_WIDTH),
					bb.min.y + rnd.nextFloat()*(Chunk.CHUNK_HEIGHT),
					bb.min.z + rnd.nextFloat()*(Chunk.CHUNK_DEPTH));
					chargeStrength[i++] = 1f+rnd.nextFloat()*MAX_CHARGE_STRENGTH;

					chargePositions[i] = new Vector3(
					bb.min.x + rnd.nextFloat()*(Chunk.CHUNK_WIDTH),
					bb.min.y + rnd.nextFloat()*(Chunk.CHUNK_HEIGHT),
					bb.min.z + rnd.nextFloat()*(Chunk.CHUNK_DEPTH));
					chargeStrength[i++] = 1f+rnd.nextFloat()*MAX_CHARGE_STRENGTH;

					chargePositions[i] = new Vector3(
					bb.min.x + rnd.nextFloat()*(Chunk.CHUNK_WIDTH),
					bb.min.y + rnd.nextFloat()*(Chunk.CHUNK_HEIGHT),
					bb.min.z + rnd.nextFloat()*(Chunk.CHUNK_DEPTH));
					chargeStrength[i++] = 1f+rnd.nextFloat()*MAX_CHARGE_STRENGTH;
				}
			}
		}

		final byte[] blockTypes = chunk.blockType;
		final Vector3 currentPos = new Vector3();

		boolean isEmpty = true;
		final int len = chargePositions.length;
		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) {
			for ( int y = 0 ; y < Chunk.BLOCKS_Y  ; y++ ) {
				for ( int z = 0 ; z < Chunk.BLOCKS_Z  ; z++ )
				{
				    currentPos.x = chunk.boundingBox.min.x + x * Chunk.BLOCK_WIDTH;
				    currentPos.y = chunk.boundingBox.min.y + y * Chunk.BLOCK_HEIGHT;
				    currentPos.z = chunk.boundingBox.min.z + z * Chunk.BLOCK_WIDTH;

					float sum = 0;
					for ( int i = 0 ; i < len ; i++)
					{
						final Vector3 v = chargePositions[i];
						final float dx = v.x - currentPos.x;
						final float dy = v.y - currentPos.y;
						final float dz = v.z - currentPos.z;
						sum += chargeStrength[i] / (dx*dx+ dy*dy + dz*dz);
					}
					final int currentIndex = x+Chunk.BLOCKS_X*y+(Chunk.BLOCKS_X*Chunk.BLOCKS_Y)*z;
					if ( sum > 0.3f ) {
						blockTypes[ currentIndex ] = Block.Type.SOLID;
						isEmpty = false;
					} else {
						blockTypes[ currentIndex ] = Block.Type.AIR;
					}
				}
			}
		}
		chunk.setEmpty(isEmpty);
	}
}
