package de.codesourcery.voxelgame.core.util;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.Pool;

import de.codesourcery.voxelgame.core.world.Chunk;


public final class ChunkList 
{
	/*
	 * Without pooling:
	 * 
	 * Frame count: 1683
     * min FPS    : 48
     * avg FPS    : 55.811646 (56.070095)
     * max FPS    : 61
     * avg. FPS jitter: 0.09625668
     *
     * With pooling:
     * 
     *  Frame count: 1676
     *  min FPS    : 47
     *  avg FPS    : 55.25358 (55.853634)
     *  max FPS    : 61
     *  avg. FPS jitter: 0.12470167     
     */
	private static final boolean USE_POOLING = false;
	
	public ChunkListEntry head;
	public ChunkListEntry tail;

	private final Pool<ChunkListEntry> nodePool;

	public static final class ChunkListEntry 
	{
		public ChunkListEntry next;
		public Chunk chunk;

		public ChunkListEntry() {
		}

		public ChunkListEntry(Chunk chunk) {
			this.chunk = chunk;
		}
	}

	public ChunkList(int initialCapacity) 
	{
		nodePool = new Pool<ChunkListEntry>(100) {

			@Override
			protected ChunkListEntry newObject() {
				return new ChunkListEntry();
			} 
		};
	}

	public List<Chunk> toList() {
		List<Chunk> result = new ArrayList<>();
		ChunkListEntry current = head;
		while ( current != null ) 
		{
			result.add( current.chunk );
			current = current.next;
		}
		return result;
	}

	public void remove(Chunk chunk) 
	{
		ChunkListEntry previous = null;
		ChunkListEntry current = head;
		while ( current != null ) {
			if ( current.chunk == chunk ) {
				break;
			}
			previous = current;
			current = current.next;
		}

		if ( current != null ) 
		{
			remove(current,previous);
		}
	}

	private void unlink(ChunkListEntry current,ChunkListEntry previous) 
	{
		if ( previous ==  null ) { // remove from head
			head = current.next;
		} else {
			previous.next = current.next;
		}
		if ( tail == current ) {
			tail = previous;
		}
	}

	public void add(Chunk chunk) 
	{
		final ChunkListEntry newEntry;
		if ( USE_POOLING ) {
			newEntry = nodePool.obtain();
			newEntry.chunk = chunk;
		} else {
			newEntry = new ChunkListEntry(chunk);
		}
		if ( head == null ) {
			head = newEntry;
			tail = newEntry;
			return;
		}

		tail.next = newEntry;
		tail = newEntry;
	}

	public void remove(ChunkListEntry entry,ChunkListEntry previous) 
	{
		unlink(entry,previous);
		entry.next = null;
		entry.chunk = null;
		
		if ( USE_POOLING ) {
			nodePool.free( entry );
		}
	}
}