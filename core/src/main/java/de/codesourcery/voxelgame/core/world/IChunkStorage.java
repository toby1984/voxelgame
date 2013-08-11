package de.codesourcery.voxelgame.core.world;

import java.io.IOException;

public interface IChunkStorage
{
	public abstract Chunk loadChunk(int chunkX,int chunkY,int chunkZ) throws IOException; 
	
	public abstract void unloadChunk(Chunk chunk) throws IOException;
}
