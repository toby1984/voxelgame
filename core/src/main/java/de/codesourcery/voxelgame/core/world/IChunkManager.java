package de.codesourcery.voxelgame.core.world;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.world.DefaultChunkManager.Hit;

public interface IChunkManager extends Disposable {

	public void visitVisibleChunks(IChunkVisitor visitor);
	
	public void chunkChanged(Chunk chunk);
	
	public void cameraMoved();

	/**
	 * Returns the tile closest to a ray's origin that is intersected by the ray.
	 * 
	 * @param ray
	 * @return
	 */
	public boolean getClosestIntersection(Ray ray, Hit hit);

	public boolean getContainingBlock(Vector3 worldCoords, Hit hit);
	
	public Chunk getChunk(int chunkX,int chunkY,int chunkZ);
	
	public Chunk maybeGetChunk(int chunkX, int chunkY, int chunkZ);	

	public boolean intersectsNonEmptyBlock(BoundingBox bb);
}