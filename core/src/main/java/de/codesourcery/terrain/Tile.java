package de.codesourcery.terrain;

public class Tile {

    public static enum TileType {
        LAND,WATER;
    }
    
    public TileType type = TileType.LAND;
    
    public boolean hasType(TileType t) {
        return t.equals( type );
    }
    
    public boolean isWater() {
        return type == TileType.WATER;
    }
}