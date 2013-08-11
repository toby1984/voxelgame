package de.codesourcery.voxelgame.core.world;

import java.io.IOException;

public interface IChunkStorage
{
	public static final boolean USE_PERSISTENT_STORAGE = true;
	
	public abstract Chunk loadChunk(int chunkX,int chunkY,int chunkZ) throws IOException; 
	
	public abstract void unloadChunk(Chunk chunk) throws IOException;
}
