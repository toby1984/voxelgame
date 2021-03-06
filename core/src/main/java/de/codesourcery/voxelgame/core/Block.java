package de.codesourcery.voxelgame.core;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import de.codesourcery.voxelgame.core.world.Chunk;


public final class Block 
{
	private static final Vector3 min = new Vector3();
	private static final Vector3 max = new Vector3();	
	
	public static final byte MAX_LIGHT_LEVEL = 15;
	public static final byte MIN_LIGHT_LEVEL = 0;
	
	public static final class Type 
	{
		// !!! Don't forget to update the MAX constant when adding new types !!!
		public static final byte AIR = 0;
		public static final byte SOLID = 1;
		public static final byte WATER = 2;
		
		public static final byte MAX = WATER;
	}
	
	public byte lightLevel=MIN_LIGHT_LEVEL;
	
	public static boolean isAirBlock(byte type) 
	{
		return type == Block.Type.AIR;
	}
	
	public static boolean isNoAirBlock(byte type) 
	{
		return type != Block.Type.AIR;
	}	
	
	public static boolean isTranslucentBlock(byte type) 
	{
		return type == Block.Type.AIR || type == Block.Type.WATER;
	}
	
	public static boolean isNoTranslucentBlock(byte type) 
	{
		return type != Block.Type.AIR && type != Block.Type.WATER;
	}		
	
	public static void populateBoundingBox(Chunk chunk,int blockX,int blockY,int blockZ,BoundingBox bb) 	{
		
		populateWithCenter( chunk,blockX,blockY,blockZ, min );
		max.set(min);
		
		min.x -= Chunk.BLOCK_WIDTH/2.0f;
		min.y -= Chunk.BLOCK_HEIGHT/2.0f;
		min.z -= Chunk.BLOCK_DEPTH/2.0f;
		
		max.x += Chunk.BLOCK_WIDTH/2.0f;
		max.y += Chunk.BLOCK_HEIGHT/2.0f;
		max.z += Chunk.BLOCK_DEPTH/2.0f;		
		bb.set( min , max );
	}
	
	public static void populateWithCenter(Chunk chunk,int blockX,int blockY,int blockZ,Vector3 result) 
	{
		final float xOrig = chunk.boundingBox.min.x;
		final float yOrig = chunk.boundingBox.min.y;
		final float zOrig = chunk.boundingBox.min.z;
		
		final float blockCenterX = xOrig + blockX * Chunk.BLOCK_WIDTH  + (Chunk.BLOCK_WIDTH*0.5f);
		final float blockCenterY = yOrig + blockY * Chunk.BLOCK_HEIGHT + (Chunk.BLOCK_HEIGHT*0.5f);				
		final float blockCenterZ = zOrig + blockZ * Chunk.BLOCK_DEPTH  + (Chunk.BLOCK_DEPTH*0.5f);		
		result.set(blockCenterX,blockCenterY,blockCenterZ);
	}
}
