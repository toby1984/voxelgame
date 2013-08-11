package de.codesourcery.voxelgame.core;

import java.io.File;

public class Constants 
{
	public static final boolean BENCHMARK_MODE = true;
	
	public static final int BENCHMARK_DURATION_SECONDS = 30;
	
	public static final boolean RESTRICT_CAMERA_TO_AIR_BLOCKS = false;	
	
	public static final boolean USE_PERSISTENT_STORAGE = true;
	
	public static final File CHUNK_STORAGE = new File("/home/tobi/tmp/chunks");

	public static final File ASSETS_PATH = new File("/home/tobi/workspace/voxelgame/assets/");	
	
	/*
	 * At least on a desktop JVM (1.7.25,64-bit), chunk 
	 * pool (while greatly reducing garbage) made the performance far WORSE (fps dropped by 10 fps)...
	 * 
	 * WITHOUT pooling:
	 * 
	 * Frame count: 1672
	 * min FPS    : 47
	 * avg FPS    : 55.14354 (55.720333)
	 * max FPS    : 61
	 * avg. FPS jitter: 0.11543062
	 * 
	 * WITH pooling:
	 * 
	 * Frame count: 1193
     * min FPS    : 15
     * avg FPS    : 46.870914 (39.74812)
     * max FPS    : 60
     * avg. FPS jitter: 0.16177703
     * 
     * 	 */
	public static final boolean USE_CHUNK_POOL = false;
}
