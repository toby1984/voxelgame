package de.codesourcery.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.badlogic.gdx.graphics.Camera;

import de.codesourcery.terrain.TileChunk.TileChunkKey;
import de.codesourcery.voxelgame.core.FPSCameraController;

public class TileChunkManager 
{
    public static final int VISIBLE_CHUNKS = 5;
    
    private int currentChunkX=-1;
    private int currentChunkZ=-1;
    
    private TerrainMesh[] visibleChunks = new TerrainMesh[VISIBLE_CHUNKS*VISIBLE_CHUNKS];
    
    private final TileChunkFactory tileChunkFactory = new TileChunkFactory();
    
    private final Map<TileChunkKey,TileChunk> chunkCache = new LinkedHashMap<TileChunkKey,TileChunk>(VISIBLE_CHUNKS*VISIBLE_CHUNKS*2,0.75f,true) 
    {
        protected boolean removeEldestEntry(Map.Entry<TileChunkKey,TileChunk> eldest) 
        {
          return size() >= VISIBLE_CHUNKS*VISIBLE_CHUNKS;   
        }
    };
    
    public TileChunkManager(ShaderManager shaderManager) 
    {
        for ( int i = 0 ; i < visibleChunks.length ; i++ ) 
        {
            visibleChunks[i]= new TerrainMesh(shaderManager);
        }
    }
    
    private void checkForChunkChange(Camera camera) 
    {
        final float chunkSize = TileChunkFactory.SIZE_IN_TILES*TerrainMesh.TILE_SIZE;
        int chunkX = (int) ((camera.position.x + chunkSize/2f)/chunkSize);
        int chunkZ = (int) ((camera.position.z + chunkSize/2f)/chunkSize);
        
        if ( chunkX != currentChunkX || chunkZ != currentChunkZ ) {
            currentChunkChanged(chunkX,chunkZ);
        }
    }
    
    public void render(FPSCameraController camera) 
    {
        checkForChunkChange( camera.camera );
        
        for (int i = 0; i < visibleChunks.length; i++) 
        {
            visibleChunks[i].render( camera );
        }
    }

    private void currentChunkChanged(int chunkX,int chunkZ) 
    {
        System.out.println("currentChunkChanged(): ("+currentChunkX+" / "+currentChunkZ+") -> ("+chunkX+"/"+chunkZ+")");
        
        // create map of all meshes that are currently visible
        final Map<TileChunkKey,TerrainMesh> loaded = new HashMap<>();
        for ( TerrainMesh mesh : visibleChunks ) 
        {
            if ( mesh.isChunkSet() ) {
                loaded.put( mesh.chunk.location , mesh );
            }
        }
        
        // determine chunks that are NOW visible
        final Set<TileChunkKey> newVisible = new HashSet<>();
        final int offset = VISIBLE_CHUNKS/2;
        for ( int z = 0 ; z < VISIBLE_CHUNKS ; z++ ) 
        {
            final int realZ = chunkZ+z-offset;
            for ( int x = 0 ; x < VISIBLE_CHUNKS ; x++ ) 
            { 
                final int realX = chunkX+x-offset;
                final TileChunkKey key = new TileChunkKey( realX , realZ );
                System.out.println("NOW VISIBLE: "+key);
                newVisible.add( key );
            }
        }

        // find meshes that will have their current chunk replaced
        final List<TerrainMesh> toReplace = new ArrayList<>();
        
        for (Iterator<Entry<TileChunkKey, TerrainMesh>> it = loaded.entrySet() .iterator(); it.hasNext();) 
        {
            final Entry<TileChunkKey, TerrainMesh> entry = it.next();
            if ( ! entry.getValue().isChunkSet() || ! newVisible.contains( entry.getKey() ) ) 
            {
                it.remove();
                toReplace.add( entry.getValue() );
            }
        }
        
        if ( toReplace.isEmpty() ) // first load 
        {
            System.out.println("Rendering for the first time");
            for ( TerrainMesh mesh : visibleChunks ) 
            {
                toReplace.add( mesh );
            }
        }

        // create new set of meshes
        final TerrainMesh[] newVisibleChunks = new TerrainMesh[VISIBLE_CHUNKS*VISIBLE_CHUNKS];
        for ( int z = 0 ; z < VISIBLE_CHUNKS ; z++ ) 
        {
            final int realZ = chunkZ+z-offset;
            for ( int x = 0 ; x < VISIBLE_CHUNKS ; x++ ) 
            { 
                final int realX = chunkX+x-offset;
                final TileChunkKey key = new TileChunkKey( realX , realZ );
                TerrainMesh existing = loaded.get( key );
                if ( existing == null ) 
                {
                    System.out.println("Not rendered yet: "+key);
                    final TileChunk chunk = getChunk( key );
                    existing = toReplace.remove(0);
                    existing.set( chunk );
                } else {
                    System.out.println("Already rendered: "+key);
                }
                newVisibleChunks[z*VISIBLE_CHUNKS+x] = existing;
            }
        }        
        
        this.visibleChunks = newVisibleChunks;
        this.currentChunkX = chunkX;
        this.currentChunkZ = chunkZ;
    }
    
    private TileChunk getChunk(TileChunkKey key) {
        
        TileChunk result = chunkCache.get( key );
        if ( result == null ) 
        {
            System.out.println("CACHE-MISS: "+key);
            result = tileChunkFactory.getChunk( key );
            chunkCache.put( key , result );
        } else {
            System.out.println("CACHE-HIT: "+key);
        }
        return result;
    }
    
    public void dispose() 
    {
        for ( TerrainMesh mesh : visibleChunks ) {
            mesh.dispose();
        }
    }
}
