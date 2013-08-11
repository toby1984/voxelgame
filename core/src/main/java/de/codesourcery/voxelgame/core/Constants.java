package de.codesourcery.voxelgame.core;

import java.io.File;

public class Constants 
{
	public static final boolean BENCHMARK_MODE = false;
	
	public static final int BENCHMARK_DURATION_SECONDS = 30;
	
	public static final boolean RESTRICT_CAMERA_TO_AIR_BLOCKS = false;	
	
	public static final boolean USE_PERSISTENT_STORAGE = true;
	
	public static final File CHUNK_STORAGE = new File("/home/tobi/tmp/chunks");

	public static final File ASSETS_PATH = new File("/home/tobi/workspace/voxelgame/assets/");	
	
	public static final boolean USE_CHUNK_POOL = true;
}
