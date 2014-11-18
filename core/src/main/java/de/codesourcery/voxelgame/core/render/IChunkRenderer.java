package de.codesourcery.voxelgame.core.render;

import de.codesourcery.voxelgame.core.world.Chunk;

public interface IChunkRenderer {

	public void render();

	public void setWireframe(boolean showWireframe);

	public boolean isWireframe();

	/**
	 *
	 * @param chunk
	 * @return number of rendered blocks
	 */
	public int setupMesh(Chunk chunk);

	public void dispose();
}