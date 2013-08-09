package de.codesourcery.voxelgame.java;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import de.codesourcery.voxelgame.core.Main;

public class MainDesktop 
{
	public static void main (String[] args) 
	{
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.useGL20 = true;
		config.vSyncEnabled = ! Main.BENCHMARK_MODE; 
		// config.fullscreen=true;
		config.width = 800;
		config.height = 600;
		new LwjglApplication(new Main(), config);
	}
}
