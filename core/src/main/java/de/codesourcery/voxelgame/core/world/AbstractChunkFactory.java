package de.codesourcery.voxelgame.core.world;

import com.badlogic.gdx.utils.Pool;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.Constants;

public abstract class AbstractChunkFactory implements IChunkFactory
{
	private final Pool<Chunk> pool = new Pool<Chunk>(10) {

		@Override
		protected Chunk newObject() {
			return new Chunk();
		}
	};

	public AbstractChunkFactory()
	{
	}

	@Override
	public final Chunk createChunk(int x,int y,int z)
	{
		final Chunk result = getChunkFromPool(x,y,z);
		initializeChunk(result);
		return result;
	}

	@Override
	public final Chunk getChunkFromPool(int chunkX,int chunkY,int chunkZ)
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

	@Override
	public final void returnChunkToPool(Chunk chunk)
	{
		if ( Constants.USE_CHUNK_POOL ) {
			synchronized(pool) {
				pool.free( chunk );
			}
		} else {
			chunk.dispose();
		}
	}

	@Override
	public final Chunk createEmptyChunk(int x,int y,int z)
	{
		final Chunk result = getChunkFromPool(x,y,z);
		result.setEmpty( true );
		return result;
	}

	@Override
	public final Chunk createSolidChunk(int x,int y,int z)
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

	protected abstract void initializeChunk(Chunk chunk);
}