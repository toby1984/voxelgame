package de.codesourcery.voxelgame.core.world;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import de.codesourcery.voxelgame.core.Block;


public class DefaultChunkStorage implements IChunkStorage
{
	private final ChunkFactory chunkFactory;
	private final String chunkDirectory;

	public DefaultChunkStorage(File chunkDirectory, ChunkFactory chunkFactory) throws IOException 
	{
		if ( ! chunkDirectory.exists() ) {
			if ( ! chunkDirectory.mkdirs() ) {
				throw new IOException("Failed to create directory "+chunkDirectory.getAbsolutePath());
			}
		}
		this.chunkDirectory = chunkDirectory.getAbsolutePath();
		this.chunkFactory = chunkFactory;
	}

	@Override
	public Chunk loadChunk(int chunkX, int chunkY,int chunkZ) throws IOException
	{
		if ( chunkY != 0 ) {
			return chunkFactory.createEmptyChunk(chunkX, chunkY,chunkZ);
		}

		Chunk result = tryLoadFromDisk(chunkX, chunkY, chunkZ);
		if ( result == null ) {
			result = chunkFactory.createChunk(chunkX, chunkY,chunkZ);
			writeToDisk( result );
		}
		return result;
	}

	private Chunk tryLoadFromDisk(int chunkX, int chunkY,int chunkZ) throws IOException 
	{
		if ( ! USE_PERSISTENT_STORAGE ) {
			return null;
		}
		final File f = createPath(chunkX,chunkY,chunkZ);
		if ( ! f.exists() ) {
			return null;
		}
		final Chunk result = chunkFactory.getChunkFromPool( chunkX , chunkY,chunkZ);
		populateFromDisk( result );
		return result;
	}

	private void writeToDisk(Chunk chunk) throws IOException 
	{
		if ( ! USE_PERSISTENT_STORAGE ) {
			return;
		}
		final Block[][][] blocks = chunk.blocks;
		final File f = createPath(chunk.x,chunk.y,chunk.z);
		final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
		try {
			for ( int xx = 0 ; xx < Chunk.BLOCKS_X ; xx++ ) 
			{
				for ( int zz = 0 ; zz < Chunk.BLOCKS_Z ; zz++ ) 
				{	
					for ( int yy = 0 ; yy < Chunk.BLOCKS_Y ; yy++ ) 
					{
						out.write( blocks[xx][yy][zz].type );
					}
				}
			}    
		} finally {
			out.close();
		}
	}

	private void populateFromDisk(Chunk chunk) throws IOException 
	{
		final Block[][][] blocks = chunk.blocks;
		final File f = createPath(chunk.x,chunk.y,chunk.z);
		final BufferedInputStream out = new BufferedInputStream(new FileInputStream(f));
		try {
			for ( int xx = 0 ; xx < Chunk.BLOCKS_X ; xx++ ) 
			{
				for ( int zz = 0 ; zz < Chunk.BLOCKS_Z ; zz++ ) 
				{	
					for ( int yy = 0 ; yy < Chunk.BLOCKS_Y ; yy++ ) 
					{
						int type = out.read();
						if ( type == -1 ) {
							throw new IOException("Unexpected EOF while loading chunk "+chunk);
						}
						blocks[xx][yy][zz].type = (byte) type;
					}
				}
			}    
		} finally {
			out.close();
		}
	}    

	private File createPath(int chunkX, int chunkY,int chunkZ) 
	{
		return new File( chunkDirectory+"/chunk_"+chunkX+"_"+chunkY+"_"+chunkZ+".chunk" );

	}

	@Override
	public void unloadChunk(Chunk chunk) throws IOException
	{
		try {
			if ( USE_PERSISTENT_STORAGE && chunk.hasChangedSinceLoad() ) 
			{
				writeToDisk( chunk );
				chunk.setChangedSinceLoad(false);
			}
		} 
		finally 
		{
			chunkFactory.returnChunkToPool( chunk ); // chunk implements Poolable so dispose() will be called by libGDX pool implementation
		}
	}
}