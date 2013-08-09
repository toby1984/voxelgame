package de.codesourcery.voxelgame.core.util;

import java.util.NoSuchElementException;

/**
 * A dynamically resizing array that provides LIFO behavior with O(1) operations for
 * <code>put()</code> , <code>removeLatest()</code> and <code>removeOldest()</code>.
 * 
 * <p>Note that the internal array is grown exponentially (2*currentSize) and
 * and memory will never be released.
 * </p>
 * <p>This class is <b>not</b> thread-safe and unless {@link #ENABLE_SANITY_CHECKS}
 * is set to <code>true</code> , will not perform any sanity checks.
 * </p>
 * @author tobias.gierke@code-sourcery.de
 *
 * @param <T>
 */
public final class LIFOArray<T> {
	
	private Object[] data;
	
	private int head=0;
	private int tail=0;
	
	private final boolean ENABLE_SANITY_CHECKS;
	
	public LIFOArray(int initialSize) 
	{
		this.data = new Object[ initialSize ];
		this.ENABLE_SANITY_CHECKS=false;
	}
	
	public LIFOArray(int initialSize,boolean enableSanityChecks) 
	{
		this.data = new Object[ initialSize ];
		this.ENABLE_SANITY_CHECKS=enableSanityChecks;
	}
	
	/**
	 * Check if this array is empty.
	 * 
	 * @return
	 */
	public boolean isEmpty() {
		return head == tail;
	}
	
	/**
	 * Returns the current size of this array.
	 * @return
	 */
	public int size() {
		return head - tail;
	}
	
	private void growArray() 
	{
		final int currentSize = head - tail;
		final Object[] newArray = new Object[ currentSize*2 ];
		System.arraycopy( data , tail , newArray , 0 , currentSize );
		this.data = newArray;
		this.tail = 0;
		this.head = currentSize;
	}
	
	/**
	 * Adds an object to the head of this LIFO array.
	 * 
	 * @param obj
	 */
	public void put(T obj) 
	{
		if ( ENABLE_SANITY_CHECKS && obj == null ) { // TODO: Debugging code, remove
			throw new IllegalArgumentException("Won't put NULL object");
		}
		data[head++] = obj;
		if ( head == data.length ) {
			growArray();
		}
	}
	
	/**
	 * Returns the object from the tail of this LIFO array.
	 * 
	 * @return
	 */
	public T removeOldest() 
	{
		if ( ENABLE_SANITY_CHECKS && (tail >= head || tail == data.length-1 ) ) {
			throw new NoSuchElementException("Array is empty?");
		}
		@SuppressWarnings("unchecked")		
		final T result = (T) data[tail];
		if ( ENABLE_SANITY_CHECKS && result == null ) {
			throw new IllegalStateException("NULL object at tail: "+tail+" head: "+head+", size: "+size());
		}
		data[tail]=null;
		tail++;
		return result;
	}
	
	/**
	 * Returns the object from the head of this LIFO queue.
	 * 
	 * @return
	 */
	public T removeLatest() 
	{
		if ( ENABLE_SANITY_CHECKS && ( head <= tail || head == 0 ) )  {
			throw new NoSuchElementException("Array is empty?");
		}		
		--head;
		@SuppressWarnings("unchecked")
		final T result =  (T) data[head];
		data[head]=null;
		return result;
	}
}