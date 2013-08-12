package de.codesourcery.voxelgame.core.world;

import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.FPSCameraController;

public interface ITickListener extends Disposable {

	/**
	 * Invoked once from the OpenGL rendering before
	 * this tick listener becomes active. 
	 */
	public void initialize();
	
	/**
	 * 
	 * @return <code>true</code> if this tick listener wants to receive
	 * further ticks or <code>false</code> if this tick listener should be discarded
	 */
	public boolean tick(FPSCameraController cameraController);
	
	/**
	 * Dispose any resources allocated by this tick listener.
	 * 
	 * Invoked by the OpenGL rendering thread.
	 */
	@Override
	public void dispose();
}
