package de.codesourcery.voxelgame.java;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import de.codesourcery.voxelgame.core.Constants;
import de.codesourcery.voxelgame.core.Main;

public class MainDesktop 
{
	public static void main (String[] args) 
	{
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.useGL20 = true;
		config.vSyncEnabled = ! Constants.BENCHMARK_MODE; 
		if ( Constants.BENCHMARK_MODE ) {
			config.foregroundFPS=0;
		}
		config.fullscreen=Constants.BENCHMARK_MODE;
		config.width = 800;
		config.height = 600;
		new LwjglApplication(new Main(), config);
	}
}
