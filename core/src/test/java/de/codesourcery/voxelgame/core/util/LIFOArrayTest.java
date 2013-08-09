package de.codesourcery.voxelgame.core.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.NoSuchElementException;

import junit.framework.TestCase;

public class LIFOArrayTest extends TestCase {

	protected static void setFinalStatic(Field field, Object newValue) throws Exception 
	{
	      field.setAccessible(true);

	      final Field modifiersField = Field.class.getDeclaredField("modifiers");
	      modifiersField.setAccessible(true);
	      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

	      field.set(null, newValue);
	   }
	
	public void testPutAndTake() 
	{
		final LIFOArray<Integer> array = new LIFOArray<>(1,true);
		
		assertTrue( array.isEmpty() );
		array.put(1);
		assertFalse( array.isEmpty() );
		assertEquals( 1 , array.size() );
		
		array.put(2);
		assertEquals( 2 , array.size() );
		array.put(3);
		assertEquals( 3 , array.size() );

		assertEquals( (Integer) 3 , array.removeLatest() );
		assertEquals( (Integer) 2 , array.removeLatest() );
		assertEquals( (Integer) 1 , array.removeLatest() );		
		
		assertTrue( array.isEmpty() );
	}
	
	public void testBug1() 
	{
		final LIFOArray<Integer> array = new LIFOArray<>(3,true);
		array.put(1);
		array.put(2);
		array.put(3);
		
		assertEquals( (Integer) 1 , array.removeOldest() );
		assertEquals( (Integer) 2 , array.removeOldest() );
		assertEquals( (Integer) 3 , array.removeOldest() );
		
		assertTrue( array.isEmpty() );
	}	
	
	public void testRemoveLatest() 
	{
		final LIFOArray<Integer> array = new LIFOArray<>(1,true);
		array.put(1);
		array.put(2);
		array.put(3);
		
		assertEquals( (Integer) 1 , array.removeOldest() );
		assertEquals( 2 , array.size() );
		
		assertEquals( (Integer) 3 , array.removeLatest() );
		assertEquals( (Integer) 2 , array.removeLatest() );
		
		assertTrue( array.isEmpty() );
		
		array.put(4);
		array.put(5);
		array.put(6);
		
		assertEquals( (Integer) 6 , array.removeLatest() );
		assertEquals( (Integer) 5 , array.removeLatest() );
		assertEquals( (Integer) 4 , array.removeLatest() );			
		
		assertTrue( array.isEmpty() );		
		
		try {
			array.removeLatest();
			fail("Should've failed");
		} catch(NoSuchElementException e) {
			// ok
		}		
	}	
	
	public void testRemoveOldest() 
	{
		final LIFOArray<Integer> array = new LIFOArray<>(1,true);
		array.put(1);
		array.put(2);
		array.put(3);
		
		assertEquals( (Integer) 1 , array.removeOldest() );
		assertEquals( 2 , array.size() );
		
		assertEquals( (Integer) 2 , array.removeOldest() );
		assertEquals( (Integer) 3 , array.removeOldest() );
		
		assertTrue( array.isEmpty() );
		
		array.put(4);
		array.put(5);
		array.put(6);
		
		assertEquals( (Integer) 4 , array.removeOldest() );
		assertEquals( (Integer) 5 , array.removeOldest() );
		assertEquals( (Integer) 6 , array.removeOldest() );			
		
		assertTrue( array.isEmpty() );		
		
		try {
			array.removeOldest();
			fail("Should've failed");
		} catch(NoSuchElementException e) {
			// ok
		}
	}	
}
