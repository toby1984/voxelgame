package de.codesourcery.voxelgame.core.world;

import de.codesourcery.voxelgame.core.util.SimplexNoise;

public class NoiseGenerator 
{
	public final int heightMapSizeX;
	public final int heightMapSizeY;
	public final int heightMapSizeZ;
	
	private SimplexNoise simplexNoise;
	private long seed;
	
	public NoiseGenerator(int heightMapSizeX,int heightMapSizeY,int heightMapSizeZ,long seed) 
	{
		this.heightMapSizeX = heightMapSizeX;
		this.heightMapSizeY = heightMapSizeY;
		this.heightMapSizeZ = heightMapSizeZ;
		this.seed = seed;
        this.simplexNoise = new SimplexNoise(seed);		
	}

	public float[] createNoise2D(float x,float y,float tileSize,int octaves,float persistance) 
	{
		return simplexNoise.createNoise2D( x ,y , heightMapSizeX , tileSize , octaves, persistance);
	}
	
	public float[] createNoise3D(float x,float y,float z,float tileSize,int octaves,float persistance) 
	{
		return simplexNoise.createNoise3D( x ,y , z, heightMapSizeX,heightMapSizeY,heightMapSizeZ, tileSize , octaves, persistance);
	}	

	public void setSeed(long seed) 
	{
        if ( simplexNoise == null || this.seed != seed ) {
            simplexNoise = new SimplexNoise(seed);
        }	    
	    this.seed = seed;
	}
}