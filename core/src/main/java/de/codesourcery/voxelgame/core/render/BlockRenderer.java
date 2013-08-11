package de.codesourcery.voxelgame.core.render;

import java.util.concurrent.CountDownLatch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.IndexBufferObject;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexBufferObject;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.world.Chunk;

public final class BlockRenderer implements Disposable {

	public static final int SIDE_FRONT = 1;
	public static final int SIDE_BACK = 2;
	public static final int SIDE_LEFT = 4;
	public static final int SIDE_RIGHT = 8;
	public static final int SIDE_TOP = 16;
	public static final int SIDE_BOTTOM = 32;

	public static final int ALL_SIDES = SIDE_FRONT | SIDE_BACK | SIDE_LEFT | SIDE_RIGHT | SIDE_TOP | SIDE_BOTTOM;

	private static final boolean DEBUG_TEXTURE_COORDS = false;

	private static final boolean DEBUG_PERFORMANCE = false;

	private static final boolean CULL_FACES = true;
	private static final boolean DEPTH_BUFFER = true;

	/*
			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					0,1,0, // normal
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_TOP_LEFT], // texture U
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_TOP_LEFT+1], // texture V
					lightFactor );	 
	 */
	private static final int ELEMENTS_PER_VERTEX = 3 + 3 + 2 + 1; // vec3(position) + vec3(normal) + vec4(color) 

	// Worst-case: checker-style "swiss cheese" with every 2nd block being empty.
	private static final int WORST_CASE_CUBE_COUNT = Chunk.BLOCKS_X*Chunk.BLOCKS_Y*Chunk.BLOCKS_Z;

	/*
	 * Each cube currently consists of 36 vertices ( 6 faces with 2 triangles each ), could be reduced
	 * by using triangle strips.
	 */	
	@SuppressWarnings("unused")
	private static final int WORST_CASE_VERTEX_COUNT = WORST_CASE_CUBE_COUNT*36;

	/* Array with UV coordinates (that index into a texture atlas texture) for triangles on each block face. 
	 * 
	 * float[blockType][uv coordinates array]
	 * 
	 * UV coordinates for vertices are stored in counter-clockwise vertex order:
	 * 
	 *  0 --3
	 *  |   |
	 *  |   |
	 *  1---2
	 * 
	 * So the UV coordinates for Vertex 1 in the FRONT triangle of the front of a SOLID block start at
	 * 
	 *  blockTextureCoords[Block.Type.SOLID][TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_BOTTOM_LEFT] , the UV coordinates for Vertex 3 in the BACK triangle of the front of a SOLID block start at
	 *  
	 *  blockTextureCoords[Block.Type.SOLID][TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_TOP_RIGHT]
	 */
	private static float[][] blockTextureCoords;

	private static final int TEX_OFFSET_FRONT  = 0; 
	private static final int TEX_OFFSET_BACK= 1*4*2; // index into UV-texture coordinates for back quad
	private static final int TEX_OFFSET_LEFT = 2*4*2; // index into UV-texture coordinates for left quad
	private static final int TEX_OFFSET_RIGHT = 3*4*2; // index into UV-texture coordinates for right quad
	private static final int TEX_OFFSET_TOP = 4*4*2; // index into UV-texture coordinates for top quad
	private static final int TEX_OFFSET_BOTTOM = 5*4*2; // index into UV-texture coordinates for bottom quad

	private static final int TEX_OFFSET_VERTEX_TOP_LEFT = 0;
	private static final int TEX_OFFSET_VERTEX_TOP_RIGHT = 2;
	private static final int TEX_OFFSET_VERTEX_BOTTOM_RIGHT = 4;
	private static final int TEX_OFFSET_VERTEX_BOTTOM_LEFT = 6;

	private final ShortArrayBuilder indexBuilder;
	private final FloatArrayBuilder vertexBuilder;

	public int maxIndexArraySize = 0;
	public int maxVertexArraySize = 0;

	private VertexBufferObject vbo;
	private IndexBufferObject ibo;

	private volatile boolean uploadDataToGPU = true;

	private int vertexCount = 0;

	public BlockRenderer(int indexArraySize,int vertexArraySize) 
	{
		indexBuilder = new ShortArrayBuilder(indexArraySize,1000);
		vertexBuilder = new FloatArrayBuilder(vertexArraySize,1000*ELEMENTS_PER_VERTEX);
	}

	public BlockRenderer() 
	{
		this(20000,100000);
	}

	public BlockRenderer begin() 
	{
		indexBuilder.begin();
		vertexBuilder.begin();
		vertexCount = 0; 
		return this;
	}

	/**
	 * Setup texture coordinates (MUST be called once before using any block renderer).
	 * 
	 * This method requires a rectangular texture (size == width).
	 * 
	 * @param textureSize texture width/height in pixels
	 * @param faceSize Heigth/width of a single block face in pixels
	 * @param textureSpacing space in pixels between any two block textures or towards the texture image boundaries 
	 */
	public static void setupTextureCoordinates(int textureSize,int faceSize,int textureSpacing) 
	{
		if ( blockTextureCoords == null ) {
			blockTextureCoords = createTextureCoordinates(textureSize,faceSize,textureSpacing);
		}
	}

	/**
	 * 
	 * @param textureSize texture size in pixels (texture is assumed to be rectangular)
	 * @param faceSize size of a single block face texture (rectangular)
	 * @param textureSpacing space in pixels between any two adjacent textures and to the texture atlas' borders
	 * @return
	 */
	private static float[][] createTextureCoordinates(int textureSize,int faceSize,int textureSpacing) 
	{
		/*
		 * The texture atlas is assumed to hold
		 * 6 textures (one for each block face) per row
		 * in the following order
		 * 
		 *  FRONT BACK LEFT RIGHT TOP BOTTOM
		 */
		float textureSpacingWidth=textureSpacing/(float) textureSize;  // width of a single block texture in texture coordinates (including spacing 
		float textureSpacingHeight=textureSpacing/(float) textureSize; // height of a single block texture in texture coordinates			

		float xOrigin = textureSpacingWidth; // X coordinate of top-left corner of front face texture for block type 0 
		float yOrigin = textureSpacingHeight; // X coordinate of top-left corner of front face texture for block type 0

		float innerTextureWidth=faceSize/(float) textureSize;  // width of a single block texture in texture coordinates
		float innerTextureHeight=faceSize/(float) textureSize; // height of a single block texture in texture coordinates		

		float[][] result = new float[ (Block.Type.MAX+1) ][];
		for ( byte type = 0 ; type <= Block.Type.MAX ; type++) 
		{
			float[] tmp = new float[6*4*2 ]; // 6 faces * 4 VerticesPerQuad * 2 UV-coordinates floats
			result[type] = tmp;
			int ptr = 0;
			for ( int face = 0 ; face < 6 ; face++ ) 
			{
				// top-left corner of quad texture
				float topLeftX = xOrigin + face*innerTextureWidth + face*textureSpacingWidth;
				float topLeftY = yOrigin + type*innerTextureHeight + type*textureSpacingHeight;

				// bottom-left corner of quad texture
				float bottomLeftX = topLeftX;
				float bottomLeftY = topLeftY + innerTextureHeight;

				// bottom-right corner of quad texture
				float bottomRightX = topLeftX+innerTextureWidth;
				float bottomRightY = topLeftY + innerTextureHeight;

				// bottom-right corner of quad texture
				float topRightX = topLeftX+innerTextureWidth;
				float topRightY = topLeftY;

				// store UV-coordinates of each quad corner, 
				// order MUST be TOP_LEFT,TOP_RIGHT,BOTTOM_RIGHT,BOTTOM_LEFT so it matches with code in BlockRenderer#addBlock() !!!
				tmp[ptr++]=topLeftX;
				tmp[ptr++]=topLeftY;

				tmp[ptr++]=topRightX;
				tmp[ptr++]=topRightY;					

				tmp[ptr++]=bottomRightX;
				tmp[ptr++]=bottomRightY;

				tmp[ptr++]=bottomLeftX;
				tmp[ptr++]=bottomLeftY;			

				if (DEBUG_TEXTURE_COORDS) {
					System.out.println("---- Block type: "+type+" , face "+face+" , TOP_LEFT     = ("+topLeftX+","+topLeftY+")");
					System.out.println("---- Block type: "+type+" , face "+face+" , TOP_RIGHT    = ("+topRightX+","+topRightY+")");
					System.out.println("---- Block type: "+type+" , face "+face+" , BOTTOM_RIGHT = ("+bottomRightX+","+bottomRightY+")");
					System.out.println("---- Block type: "+type+" , face "+face+" , BOTTOM_LEFT  = ("+bottomLeftX+","+bottomLeftY+")");
				}
			}
		}
		return result;
	}	

	public void addBlock(float centerX,float centerY,float centerZ, float halfBlockSize, float lightFactor,byte blockType ,int sideMask ) 
	{
		final float[] textureUV = blockTextureCoords[ blockType ];

		int p0,p1,p2,p3;
		if ( (sideMask & SIDE_FRONT ) != 0 ) {

			p0 = vertexCount;
			p1 = vertexCount+1;
			p2 = vertexCount+2;
			p3 = vertexCount+3;
			vertexCount += 4;

			// top left
			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_TOP_LEFT], // texture U
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_TOP_LEFT+1], // texture V					
					lightFactor );

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_TOP_RIGHT], // texture U
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_TOP_RIGHT+1], // texture V					
					lightFactor);

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_BOTTOM_RIGHT], // texture U		
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_BOTTOM_RIGHT+1], // texture V						
					lightFactor );

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_BOTTOM_LEFT], // texture U
					textureUV[TEX_OFFSET_FRONT+TEX_OFFSET_VERTEX_BOTTOM_LEFT+1], // texture V					
					lightFactor );

			indexBuilder.put( (short) p0,(short) p3, (short) p2 , (short) p0 , (short) p2 , (short) p1 );
		}

		if ( (sideMask & SIDE_BACK ) != 0 ) {

			p0 = vertexCount;
			p1 = vertexCount+1;
			p2 = vertexCount+2;
			p3 = vertexCount+3;
			vertexCount += 4;

			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					0,0,-1, // normal
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_TOP_RIGHT], // texture U
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_TOP_RIGHT+1], // texture V					
					lightFactor );

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					0,0,-1, // normal
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_TOP_LEFT], // texture U	
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_TOP_LEFT+1], // texture V	
					lightFactor );

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					0,0,-1, // normal
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_BOTTOM_LEFT], // texture U		
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_BOTTOM_LEFT+1], // texture V
					lightFactor ); 

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					0,0,-1, // normal
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_BOTTOM_RIGHT], // texture UV
					textureUV[TEX_OFFSET_BACK+TEX_OFFSET_VERTEX_BOTTOM_RIGHT+1], // texture V	
					lightFactor ); 

			indexBuilder.put( (short) p2,(short) p3, (short) p1 , (short) p1 , (short) p3 , (short) p0 );
		}		

		if ( (sideMask & SIDE_LEFT ) != 0 ) {

			p0 = vertexCount;
			p1 = vertexCount+1;
			p2 = vertexCount+2;
			p3 = vertexCount+3;
			vertexCount += 4;

			vertexBuilder.put( centerX-halfBlockSize  , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					-1,0,0, // normal
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_TOP_LEFT], // texture U
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_TOP_LEFT+1], // texture V
					lightFactor );

			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					-1,0,0, // normal
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_TOP_RIGHT], // texture U
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_TOP_RIGHT+1], // texture V
					lightFactor );

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					-1,0,0, // normal
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_BOTTOM_RIGHT], // texture U
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_BOTTOM_RIGHT+1], // texture V
					lightFactor );

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					-1,0,0, // normal
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_BOTTOM_LEFT], // texture U
					textureUV[TEX_OFFSET_LEFT+TEX_OFFSET_VERTEX_BOTTOM_LEFT+1], // texture V
					lightFactor );

			indexBuilder.put( (short) p0,(short) p3, (short) p2 , (short) p0 , (short) p2 , (short) p1 );
		}	

		if ( (sideMask & SIDE_RIGHT ) != 0 ) {

			p0 = vertexCount;
			p1 = vertexCount+1;
			p2 = vertexCount+2;
			p3 = vertexCount+3;
			vertexCount += 4;

			vertexBuilder.put( centerX+halfBlockSize  , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					1,0,0, // normal
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_TOP_LEFT], // texture U		
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_TOP_LEFT+1], // texture V
					lightFactor ); 

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					1,0,0, // normal
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_TOP_RIGHT], // texture U
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_TOP_RIGHT+1], // texture V
					lightFactor ); 

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					1,0,0, // normal
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_BOTTOM_RIGHT], // texture U
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_BOTTOM_RIGHT+1], // texture V
					lightFactor ); 

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					1,0,0, // normal
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_BOTTOM_LEFT], // texture U
					textureUV[TEX_OFFSET_RIGHT+TEX_OFFSET_VERTEX_BOTTOM_LEFT+1], // texture V
					lightFactor ); 

			indexBuilder.put( (short) p0,(short) p3, (short) p2 , (short) p0 , (short) p2 , (short) p1 );
		}		

		if ( (sideMask & SIDE_TOP ) != 0 ) {

			p0 = vertexCount;
			p1 = vertexCount+1;
			p2 = vertexCount+2;
			p3 = vertexCount+3;
			vertexCount += 4;

			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					0,1,0, // normal
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_TOP_LEFT], // texture U
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_TOP_LEFT+1], // texture V
					lightFactor );

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					0,1,0, // normal
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_TOP_RIGHT], // texture U
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_TOP_RIGHT+1], // texture V
					lightFactor ); 

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,1,0, // normal
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_BOTTOM_RIGHT], // texture U
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_BOTTOM_RIGHT+1], // texture V
					lightFactor ); 

			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,1,0, // normal
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_BOTTOM_LEFT], // texture U
					textureUV[TEX_OFFSET_TOP+TEX_OFFSET_VERTEX_BOTTOM_LEFT+1], // texture V
					lightFactor ); 

			indexBuilder.put( (short) p0,(short) p3, (short) p2 , (short) p0 , (short) p2 , (short) p1 );
		}	

		if ( (sideMask & SIDE_BOTTOM ) != 0 ) {

			p0 = vertexCount;
			p1 = vertexCount+1;
			p2 = vertexCount+2;
			p3 = vertexCount+3;
			vertexCount += 4;

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					0,-1,0, // normal
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_TOP_LEFT], // texture UV
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_TOP_LEFT+1], // texture UV
					lightFactor ); 

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					0,-1,0, // normal
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_TOP_RIGHT], // texture UV
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_TOP_RIGHT+1], // texture UV
					lightFactor ); 

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,-1,0, // normal
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_BOTTOM_RIGHT], // texture UV
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_BOTTOM_RIGHT+1], // texture UV
					lightFactor ); // color BLUE

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,-1,0, // normal
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_BOTTOM_LEFT], // texture UV
					textureUV[TEX_OFFSET_BOTTOM+TEX_OFFSET_VERTEX_BOTTOM_LEFT+1], // texture UV
					lightFactor ); // color WHITE

			indexBuilder.put( (short) p3,(short) p0, (short) p1 , (short) p3 , (short) p1 , (short) p2 );
		}			
	}

	private VertexBufferObject createVBO(int vertexCount) 
	{
		final VertexAttribute lightFactorAttribute  = new VertexAttribute( 250 , 1 , "a_lightFactor" ); // value in the range 0...1
		final VertexAttribute[] attrs = new VertexAttribute[] 
				{
				new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
				new VertexAttribute(Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
				new VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE),
				lightFactorAttribute // a float in the range [0...1] (no light ... full light)
				};
		return new VertexBufferObject(true, vertexCount , attrs );		
	}	

	public BlockRenderer end() 
	{
		indexBuilder.end();
		vertexBuilder.end();		

		if ( indexBuilder.actualSize() > maxIndexArraySize ) {
			maxIndexArraySize = indexBuilder.actualSize();
		}
		if ( vertexBuilder.actualSize() > maxVertexArraySize ) {
			maxVertexArraySize = vertexBuilder.actualSize();
		}

		uploadDataToGPU = true;
		return this;
	}

	public void render(ShaderProgram shader) 
	{
		final int vertexCount = vertexBuilder.actualSize() / ELEMENTS_PER_VERTEX;
		if ( vertexCount == 0 ) {
			return;
		}

		if ( vbo == null ||  vbo.getNumMaxVertices() < vertexCount  ) 
		{
			if ( vbo != null ) {
				vbo.dispose();
				vbo = null;
			}
			if ( DEBUG_PERFORMANCE ) {
				System.out.println("Creating VBO for "+vertexCount+" vertices.");
			}
			vbo = createVBO( vertexCount );
			uploadDataToGPU = true;
		}

		final int indexCount = indexBuilder.actualSize();
		if ( ibo == null ||  ibo.getNumMaxIndices() < indexCount  ) 
		{
			if ( ibo != null ) {
				ibo.dispose();
				ibo = null;
			}
			if ( DEBUG_PERFORMANCE ) {
				System.out.println("Creating VBO for "+indexCount+" indices.");
			}
			ibo = createIBO( indexCount );
			uploadDataToGPU = true;
		}	

		if ( CULL_FACES ) {
			Gdx.graphics.getGL20().glEnable( GL20.GL_CULL_FACE );
		}
		if ( DEPTH_BUFFER ) {
			Gdx.graphics.getGL20().glEnable(GL11.GL_DEPTH_TEST);
		}

		vbo.bind( shader );
		ibo.bind();

		if ( uploadDataToGPU ) {
			vbo.setVertices( vertexBuilder.array , 0 , vertexBuilder.actualSize() );
			ibo.setIndices( indexBuilder.array , 0 , indexBuilder.actualSize() );
			uploadDataToGPU = false;
		}

		int indicesRemaining = indexCount;
		while ( indicesRemaining > 9 ) 
		{
			int indicesToDraw = indicesRemaining > 65535 ? 65535 : indicesRemaining;
			Gdx.graphics.getGL20().glDrawElements(GL20.GL_TRIANGLES, indicesToDraw , GL20.GL_UNSIGNED_SHORT , 0);
			indicesRemaining -= indicesToDraw;
		}

		if ( DEPTH_BUFFER ) {
			Gdx.graphics.getGL20().glDisable(GL11.GL_DEPTH_TEST);
		}

		if ( CULL_FACES ) {
			Gdx.graphics.getGL20().glDisable( GL20.GL_CULL_FACE );
		}

		ibo.unbind();
		vbo.unbind(shader);
	}

	private IndexBufferObject createIBO(int indexCount) {
		return new IndexBufferObject(true,indexCount); 
	}		

	@Override
	public void dispose() 
	{
		if ( vbo != null || ibo != null ) // release buffers on OpenGL thread
		{
			final CountDownLatch latch = new CountDownLatch(1);
			Gdx.app.postRunnable( new Runnable() {

				@Override
				public void run() 
				{
					try 
					{
						if ( vbo != null ) {
							vbo.dispose();
							vbo = null;
						}
						if ( ibo != null ) {
							ibo.dispose();
							ibo = null;
						}
					}
					finally 
					{
						latch.countDown();
					}
				}
			});
			try {
				latch.await();
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}

		uploadDataToGPU = true;
		maxVertexArraySize = maxIndexArraySize = 0;
	}
}