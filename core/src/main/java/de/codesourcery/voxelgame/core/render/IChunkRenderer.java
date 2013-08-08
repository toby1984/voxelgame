package de.codesourcery.voxelgame.core.render;

import de.codesourcery.voxelgame.core.world.Chunk;

public interface IChunkRenderer {

	public void render();

	public int setupMesh(Chunk chunk);
	
	public void dispose();
}