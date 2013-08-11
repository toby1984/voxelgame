package de.codesourcery.voxelgame.core.world;

import java.io.IOException;

public interface IChunkStorage
{
	/**
	 * Loads a specific chunk from persistent storage.
	 * 
	 * <p>Chunks returned by this method need to be released 
	 * by calling {@link #releaseChunk(Chunk)}. You might want
	 * to call {@link #saveChunk(Chunk)} beforehand to make
	 * sure any changes to dirty chunks are saved to 
	 * disk before it is disposed.
	 * </p>
	 * @param chunkX
	 * @param chunkY
	 * @param chunkZ
	 * @return
	 * @throws IOException
	 */
	public Chunk loadChunk(int chunkX,int chunkY,int chunkZ) throws IOException; 
	
	/**
	 * Saves a specific chunk to persistent storage.
	 * 
	 * @param chunk
	 * @throws IOException
	 */
	public void saveChunk(Chunk chunk) throws IOException;
	
	/**
	 * Releases all resources associated with a given chunk.
	 * 
	 * <p>This method <b>must</b> be called on the OpenGL rendering thread
	 * if the chunk has an active VBO assigned , otherwise race conditions
	 * will ensue...
	 * </p>
	 * @param chunk
	 * @throws IOException
	 * @see #saveChunk(Chunk)
	 * @see Chunk#dispose()
	 */
	public void releaseChunk(Chunk chunk) throws IOException;
}
