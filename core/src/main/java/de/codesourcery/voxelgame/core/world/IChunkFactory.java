package de.codesourcery.voxelgame.core.world;

public interface IChunkFactory {

	public Chunk createChunk(int x, int y, int z);

	public Chunk getChunkFromPool(int chunkX, int chunkY, int chunkZ);

	public void returnChunkToPool(Chunk chunk);

	public Chunk createEmptyChunk(int x, int y, int z);

	public Chunk createSolidChunk(int x, int y, int z);

}