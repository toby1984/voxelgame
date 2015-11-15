package de.codesourcery.terrain;

public class TileChunk 
{
    public final TileChunkKey location;
    public final int sizeInTiles;
    
    public final Tile[] tiles;
    public final float[] heightMap;
    
    public static final class TileChunkKey 
    {
        public final int x;
        public final int z;
        
        public TileChunkKey(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public int hashCode() {
            final int result = 31 + x;
            return 31 * result + z;
        }

        @Override
        public boolean equals(Object obj) 
        {
            if (obj instanceof TileChunkKey) {
                final TileChunkKey other = (TileChunkKey) obj;
                return (x == other.x) && (z == other.z);
            }
            return false;
        }
        
        @Override
        public String toString() {
            return "TileChunkKey[ "+x+" / "+z+"]";
        }
    }
    
    public TileChunk(TileChunkKey location,int sizeInTiles,float[] heightMap) 
    {
        this.location = location;
        this.sizeInTiles = sizeInTiles;
        this.heightMap = heightMap;
        
        this.tiles = new Tile[sizeInTiles*sizeInTiles];
        for ( int i = 0 ; i < sizeInTiles*sizeInTiles;i++) {
            tiles[i]=new Tile();
        }
    }
    
    public Tile getTile(int x,int z) {
        return tiles[ z*sizeInTiles + x ];
    }
    
    @Override
    public String toString() {
        return "TileChunk [ "+location.x+" / "+location.z+" ]";
    }
}
