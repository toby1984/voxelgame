package de.codesourcery.voxelgame.core.util;

import java.util.NoSuchElementException;

import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;

/**
 * A thread-safe bounded LIFO queue that only holds unique elements.
 * 
 * @author tgierke
 *
 * @param <T>
 */
public final class BoundedUniqueLIFOQueue<T> implements Disposable {

	private final LIFOArray<T> data;
	private final IntMap<T> map;
	private final int maxSize;
	private volatile boolean terminate;
	private int size;

	public static final class ShutdownException extends RuntimeException {

		public ShutdownException() {
			super("Queue destroyed");
		}
	}

	public BoundedUniqueLIFOQueue(int initialSize,int maxSize) 
	{
		if ( maxSize < initialSize || initialSize < 1 ) {
			throw new IllegalArgumentException("Invalid size(s)");
		}
		this.data = new LIFOArray<T>(initialSize,false);
		this.map = new IntMap<T>(initialSize);
		this.maxSize = maxSize;
	}

	/**
	 * Adds an object to the head of the queue.
	 * 
	 * If this object is already part of the queue.
	 * @param obj
	 */
	public void insert(T obj) 
	{
		if ( terminate ) {
			return;
		}		
		synchronized(data) 
		{
			final int hashCode = obj.hashCode();
			T existing = map.get( hashCode ); 
			if ( existing != null && existing.equals( obj ) ) // check equals() as well to properly handle hash collision
			{
				// System.err.println("Already queued: "+obj);
				return;
			}

			if ( size == maxSize) 
			{
				// discard element from tail
				T removed = data.removeOldest();
				map.remove( removed.hashCode() );
				size--;
			}

			data.put(obj);
			map.put( hashCode , obj );

			size++;

			data.notify();
		}
	}

	public T take() throws InterruptedException , ShutdownException
	{
		synchronized(data) 
		{
			if ( terminate ) {
				throw new ShutdownException();				
			}				
			while ( size == 0 ) {
				data.wait();
				if ( terminate ) {
					throw new ShutdownException();
				}
			}
			try {
				final T removed = data.removeLatest();
				map.remove( removed.hashCode() );
				size--;
				return removed;
			} 
			catch(NoSuchElementException e) 
			{
				System.err.println("NoSuchElementException, size: "+size+" ,map: "+map);
				throw e;
			}
		}
	}

	@Override
	public void dispose() {
		terminate = true;
		synchronized(data) 
		{
			data.notifyAll();
		}
	}
}
