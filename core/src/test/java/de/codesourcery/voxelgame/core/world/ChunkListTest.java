package de.codesourcery.voxelgame.core.world;

import java.util.Arrays;

import de.codesourcery.voxelgame.core.util.ChunkList;

import junit.framework.TestCase;

public class ChunkListTest extends TestCase {

	private ChunkList list;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		list = new ChunkList(50);
	}
	
	private static Chunk createChunk(int x,int y,int z) {
		Chunk result = new Chunk();
		result.initialize(x,y,z);
		return result;
	}
	
	public void testAddOne() 
	{
		final Chunk c = createChunk(1,2,3);
		list.add( c );
		assertEquals( Arrays.asList( c ) , list.toList() );
	}
	
	public void testAddTwo() {
		
		final Chunk c1 = createChunk(1,2,3);
		final Chunk c2 = createChunk(1,2,3);
		list.add( c1 );
		list.add( c2 );
		assertEquals( Arrays.asList( c1,c2 ) , list.toList() );
	}
	
	public void testAddThree() {
		
		final Chunk c1 = createChunk(1,2,3);
		final Chunk c2 = createChunk(1,2,3);
		final Chunk c3 = createChunk(1,2,3);
		list.add( c1 );
		list.add( c2 );
		list.add( c3 );
		assertEquals( Arrays.asList( c1,c2,c3 ) , list.toList() );
	}		
	
	public void testRemoveHead() {
		
		final Chunk c1 = createChunk(1,2,3);
		final Chunk c2 = createChunk(1,2,3);
		final Chunk c3 = createChunk(1,2,3);
		list.add( c1 );
		list.add( c2 );
		list.add( c3 );
		assertEquals( Arrays.asList( c1,c2,c3 ) , list.toList() );
		list.remove(c1);
		assertEquals( Arrays.asList( c2,c3 ) , list.toList() );		
	}		
	
	public void testRemoveMiddle() {
		
		final Chunk c1 = createChunk(1,2,3);
		final Chunk c2 = createChunk(1,2,3);
		final Chunk c3 = createChunk(1,2,3);
		list.add( c1 );
		list.add( c2 );
		list.add( c3 );
		assertEquals( Arrays.asList( c1,c2,c3 ) , list.toList() );
		list.remove(c2);
		assertEquals( Arrays.asList( c1,c3 ) , list.toList() );		
	}	
	
	public void testRemoveTail() {
		
		final Chunk c1 = createChunk(1,2,3);
		final Chunk c2 = createChunk(1,2,3);
		final Chunk c3 = createChunk(1,2,3);
		list.add( c1 );
		list.add( c2 );
		list.add( c3 );
		assertEquals( Arrays.asList( c1,c2,c3 ) , list.toList() );
		list.remove(c3);
		assertEquals( Arrays.asList( c1,c2 ) , list.toList() );		
	}	
	
	public void testRemoveAll() {
		
		final Chunk c1 = createChunk(1,2,3);
		final Chunk c2 = createChunk(1,2,3);
		final Chunk c3 = createChunk(1,2,3);
		list.add( c1 );
		list.add( c2 );
		list.add( c3 );
		assertEquals( Arrays.asList( c1,c2,c3 ) , list.toList() );
		list.remove(c2);
		list.remove(c3);
		list.remove(c1);
		assertEquals( Arrays.asList() , list.toList() );		
	}	
}
