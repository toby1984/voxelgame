package de.codesourcery.voxelgame.core.render;

public final class ShortArrayBuilder {

	public short[] array;
	private int currentOffset=0;
	private int sizeIncrement;
	
	public ShortArrayBuilder(int initialSize,int sizeIncrement) 
	{
		if ( initialSize < 1 ) {
			throw new IllegalArgumentException("initial size must be >= 1");
		}
		if ( sizeIncrement < 1 ) {
			throw new IllegalArgumentException("size increment must be >= 1");
		}
		
		this.array = new short[ initialSize ];
		this.sizeIncrement = sizeIncrement;
	}
	
	public ShortArrayBuilder put(short value) 
	{
		if ( currentOffset == array.length-1) {
			extendArray(1);
		}
		array[currentOffset++]=value;
		return this;
	}
	
	public ShortArrayBuilder put(short[] data) 
	{
		final int length = data.length;
		if ( currentOffset+length>= array.length-1) {
			extendArray(length);
		}
		System.arraycopy( data , 0 , array , currentOffset , length);
		currentOffset += length;
		return this;
	}	
	
	public ShortArrayBuilder put(short[] data,int offset,int count) 
	{
		if ( currentOffset+count >= array.length-1) {
			extendArray(count);
		}
		System.arraycopy( data , offset , array , currentOffset , count );
		currentOffset += count;
		return this;
	}		
	
	public ShortArrayBuilder begin() 
	{
		currentOffset = 0;
		return this;
	}
	
	public int end() 
	{
		return currentOffset;
	}	
	
	public int actualSize() {
		return currentOffset;
	}
	
	public ShortArrayBuilder put(short value1,short value2,short value3) 
	{
		if ( currentOffset+3 >= array.length-1) {
			extendArray(3);
		}
		array[currentOffset++]=value1;
		array[currentOffset++]=value2;
		array[currentOffset++]=value3;
		return this;
	}	
	
	public ShortArrayBuilder put(short value1,short value2,short value3,short value4) 
	{
		if ( currentOffset+4 >= array.length-1) {
			extendArray(4);
		}
		array[currentOffset++]=value1;
		array[currentOffset++]=value2;
		array[currentOffset++]=value3;
		array[currentOffset++]=value4;
		return this;
	}	
	
	public ShortArrayBuilder put(short value1,short value2,short value3,short value4,short value5 , short value6) 
	{
		if ( currentOffset+6 >= array.length-1) {
			extendArray(6);
		}
		array[currentOffset++]=value1;
		array[currentOffset++]=value2;
		array[currentOffset++]=value3;
		array[currentOffset++]=value4;
		array[currentOffset++]=value5;
		array[currentOffset++]=value6;
		return this;
	}	

	private void extendArray(int minIncrement) 
	{
		int newSize = sizeIncrement < minIncrement ? minIncrement : sizeIncrement;
		short[] tmp = new short[ array.length + newSize ];
		System.arraycopy(array, 0 , tmp , 0 , currentOffset);
		array=tmp;
	}
}