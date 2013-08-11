package de.codesourcery.voxelgame.core.util;

import java.util.Random;

import de.codesourcery.voxelgame.core.Block;

/**
 * Benchmark comparing 3-dimension array access times against
 * accessing a one-dimensional array.
 *
 * @author tobias.gierke@voipfuture.com
 */
public class ArrayBenchmark {

	public static final int ITERATION_COUNT=3000000;
	
	public static final int ARRAY_SIZE = 32;
	
	public static void main(String[] args) {
		
		new ArrayBenchmark().run();
	}

	protected static final class IntVector {
		public final int x;
		public final int y;
		public final int z;
		
		public IntVector(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
	
	private void run() 
	{
		IntVector[] coords = new IntVector[ ITERATION_COUNT ];
		Random rnd = new Random(0xdeadbeef);
		for ( int i = 0 ; i < ITERATION_COUNT ; i++ ) 
		{
			int x = (int) rnd.nextFloat()*ARRAY_SIZE;
			int y = (int) rnd.nextFloat()*ARRAY_SIZE;
			int z = (int) rnd.nextFloat()*ARRAY_SIZE;
			coords[i] = new IntVector(x, y, z);
		}
		
		Block[][][] testArray = create3DArray();
		
		// 3d array access => 207 ms
		for ( int i = 0 ; i < 30 ; i++ ) {
			runTest3DArray(coords,testArray);
		}
		System.out.println("1d array test");

		Block[] testArray2 = create1DArray();		
		for ( int i = 0 ; i < 30 ; i++ ) {
			runTest1DArray(coords,testArray2);
		}		
	}

	private Block[][][] create3DArray() 
	{
		final Block[][][] testArray = new Block[ ARRAY_SIZE][][];
		for ( int x = 0 ; x < ARRAY_SIZE ; x++ ) {
			testArray[x] = new Block[ARRAY_SIZE][];
			for ( int y =0 ; y < ARRAY_SIZE ; y++ ) {
				testArray[x][y]= new Block[ARRAY_SIZE];
				for ( int z = 0 ; z < ARRAY_SIZE ; z++ ) {
					Block block = new Block();
					block.lightLevel = (byte) (x+y+z);
					testArray[x][y][z] = block;
					
				}
			}
		}
		return testArray;
	}
	
	private Block[] create1DArray() 
	{
		final int arraySize = ARRAY_SIZE*ARRAY_SIZE*ARRAY_SIZE;
		final Block[] testArray = new Block[ arraySize ];
		for ( int x = 0 ; x < ARRAY_SIZE ; x++ ) 
		{
			for ( int y =0 ; y < ARRAY_SIZE ; y++ ) 
			{
				for ( int z = 0 ; z < ARRAY_SIZE ; z++ ) 
				{
					Block block = new Block();
					block.lightLevel = (byte) (x+y+z);
					testArray[x+(y*ARRAY_SIZE)+(ARRAY_SIZE*ARRAY_SIZE)*z] = block;
				}
			}
		}		
		return testArray;
	}	
	
	private void runTest3DArray(IntVector[] coords,Block[][][] testArray ) {
		int count = 0;
		long time = - System.currentTimeMillis();
		for ( int j =0 ; j < 20 ; j++ ) {
			for ( int i = 0 ; i < ITERATION_COUNT ; i++ ) 
			{
				IntVector v = coords[i];
				if ( testArray[v.x][v.y][v.z].lightLevel == 0 ) {
					count++;
				}
			}
		}
		time += System.currentTimeMillis();
		System.out.println("Count = "+count+" , time: "+time);
	}
	
	private void runTest1DArray(IntVector[] coords,Block[] testArray ) 
	{
		int count = 0;
		long time = - System.currentTimeMillis();
		for ( int j =0 ; j < 20 ; j++ ) {
			for ( int i = 0 ; i < ITERATION_COUNT ; i++ ) 
			{
				IntVector v = coords[i];
				if ( testArray[v.x+v.y*(ARRAY_SIZE)+(ARRAY_SIZE*ARRAY_SIZE)*v.z].lightLevel == 0 ) {
					count++;
				}
			}
		}
		time += System.currentTimeMillis();
		System.out.println("Count = "+count+" , time: "+time);
	}	
}
