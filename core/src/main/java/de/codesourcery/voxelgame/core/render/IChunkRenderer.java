package de.codesourcery.voxelgame.core.render;

import java.util.List;

import com.badlogic.gdx.graphics.g3d.lights.PointLight;

import de.codesourcery.voxelgame.core.FPSCameraController;
import de.codesourcery.voxelgame.core.world.Chunk;

public interface IChunkRenderer {

	public abstract void render(FPSCameraController cameraController,PointLight light, List<Chunk> chunks);

	public abstract void dispose();
}