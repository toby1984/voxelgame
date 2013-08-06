package de.codesourcery.voxelgame.core.util;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.voxelgame.core.world.Chunk;


public final class ChunkList 
{
	public ChunkListEntry head;
	public ChunkListEntry tail;
	
	public static final class ChunkListEntry 
	{
		public ChunkListEntry(Chunk chunk) {
			this.chunk = chunk;
		}
		
		public ChunkListEntry next;
		public Chunk chunk;
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
		final ChunkListEntry newEntry = new ChunkListEntry(chunk);
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
	}
}