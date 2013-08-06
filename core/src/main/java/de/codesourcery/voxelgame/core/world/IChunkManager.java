package de.codesourcery.voxelgame.core.world;

import java.util.List;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.world.DefaultChunkManager.Hit;

public interface IChunkManager extends Disposable {

	public abstract List<Chunk> getVisibleChunks();
	
	public abstract void chunkChanged(Chunk chunk);

	public abstract void cameraMoved();

	/**
	 * Returns the tile closest to a ray's origin that is intersected by the ray.
	 * 
	 * @param ray
	 * @return
	 */
	public abstract boolean getClosestIntersection(Ray ray, Hit hit);

	public abstract boolean getContainingBlock(Vector3 worldCoords, Hit hit);
	
	public abstract Chunk getChunk(int chunkX,int chunkY,int chunkZ);
	
	public Chunk maybeGetChunk(int chunkX, int chunkY, int chunkZ);	

	public abstract boolean intersectsNonEmptyBlock(BoundingBox bb);

	public abstract void setChunkStorage(IChunkStorage tileManager);
}