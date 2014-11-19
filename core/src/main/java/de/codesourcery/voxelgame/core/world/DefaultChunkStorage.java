package de.codesourcery.voxelgame.core.world;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import de.codesourcery.voxelgame.core.Constants;


public class DefaultChunkStorage implements IChunkStorage
{
	public static boolean DELETE_CHUNK_CACHE_ON_START = true;

	private final IChunkFactory chunkFactory;
	private final String chunkDirectory;

	public DefaultChunkStorage(File chunkDirectory, IChunkFactory chunkFactory) throws IOException
	{
		if ( DELETE_CHUNK_CACHE_ON_START )
		{
			System.out.println("Deleting chunk cache folder "+chunkDirectory);
			deleteFolder( chunkDirectory );
		}
		if ( ! chunkDirectory.exists() ) {
			if ( ! chunkDirectory.mkdirs() ) {
				throw new IOException("Failed to create directory "+chunkDirectory.getAbsolutePath());
			}
		}
		this.chunkDirectory = chunkDirectory.getAbsolutePath();
		this.chunkFactory = chunkFactory;
	}

	private static void deleteFolder(File file) throws IOException
	{
		if ( file.isFile() ){
			file.delete();
			return;
		}

        final File[] listFiles = file.listFiles();
		if ( listFiles != null ) {
			for ( final File f : listFiles  ) {
				deleteFolder(f);
			}
		}
		file.delete();
	}

	@Override
	public Chunk loadChunk(int chunkX, int chunkY,int chunkZ) throws IOException
	{
		Chunk result = null;
		if ( Constants.USE_PERSISTENT_STORAGE )
		{
			try {
				result = tryLoadFromDisk(chunkX, chunkY, chunkZ);
			}
			catch(final IOException e) {
				e.printStackTrace();
			}
		}
		if ( result == null ) {
			result = chunkFactory.createChunk(chunkX, chunkY,chunkZ);
			if ( Constants.USE_PERSISTENT_STORAGE )
			{
				try {
					writeToDisk( result );
				} catch(final IOException e) {
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	private Chunk tryLoadFromDisk(int chunkX, int chunkY,int chunkZ) throws IOException
	{
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
		final File f = createPath(chunk.x,chunk.y,chunk.z);
		final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));

		try {
			out.write( chunk.blockType , 0 , chunk.blockType.length );
		} finally {
			out.close();
		}
	}

	private void populateFromDisk(Chunk chunk) throws IOException
	{
		final File f = createPath(chunk.x,chunk.y,chunk.z);
		final BufferedInputStream out = new BufferedInputStream(new FileInputStream(f));
		try
		{
			final int read = out.read( chunk.blockType , 0 , chunk.blockType.length );
			if ( read != chunk.blockType.length ) {
				throw new IOException("Premature end of input, tried to read "+chunk.blockType.length+" bytes but got only "+read);
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
	public void saveChunk(Chunk chunk) throws IOException
	{
		if ( Constants.USE_PERSISTENT_STORAGE && chunk.hasChangedSinceLoad() )
		{
			writeToDisk( chunk );
			chunk.setChangedSinceLoad(false);
		}
	}

	@Override
	public void releaseChunk(Chunk chunk) throws IOException
	{
		chunkFactory.returnChunkToPool( chunk ); // chunk implements Poolable so dispose() will be called by libGDX pool implementation
	}
}