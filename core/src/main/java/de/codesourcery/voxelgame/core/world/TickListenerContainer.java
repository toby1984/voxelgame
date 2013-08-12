package de.codesourcery.voxelgame.core.world;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.voxelgame.core.FPSCameraController;

public final class TickListenerContainer {

	private final List<ITickListener> tickListeners = new ArrayList<ITickListener>(100);
	
	private final List<ITickListener> toRemove = new ArrayList<>(100);
	
	public void add(ITickListener toAdd) 
	{
		if (toAdd == null) {
			throw new IllegalArgumentException("toAdd must not be null");
		}
		toAdd.initialize();
		tickListeners.add( toAdd );
	}
	
	public void tick(FPSCameraController cameraController) 
	{
		for ( ITickListener l : tickListeners ) {
			if ( ! l.tick( cameraController ) ) {
				toRemove.add( l );
			}
		}
		
		if ( ! toRemove.isEmpty() ) 
		{
			for ( ITickListener l : toRemove ) {
				l.dispose();
			}
			tickListeners.removeAll( toRemove );
			toRemove.clear();				
		}
	}
}