package de.codesourcery.voxelgame.core.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.IndexBufferObject;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexBufferObject;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.world.Chunk;

public final class BlockRenderer implements Disposable {

	public static final int SIDE_FRONT = 1;
	public static final int SIDE_BACK = 2;
	public static final int SIDE_LEFT = 4;
	public static final int SIDE_RIGHT = 8;
	public static final int SIDE_TOP = 16;
	public static final int SIDE_BOTTOM = 32;
	
	public static final int ALL_SIDES = SIDE_FRONT | SIDE_BACK | SIDE_LEFT | SIDE_RIGHT | SIDE_TOP | SIDE_BOTTOM;

	private static final boolean DEBUG_PERFORMANCE = false;
	
	private static final boolean CULL_FACES = true;
	private static final boolean DEPTH_BUFFER = true;

	private static final int ELEMENTS_PER_VERTEX = 3 + 3 + 4; // vec3(position) + vec3(normal) + vec4(color) 

	 // Worst-case: checker-style "swiss cheese" with every 2nd block being empty.
	private static final int WORST_CASE_CUBE_COUNT = Chunk.BLOCKS_X*Chunk.BLOCKS_Y*Chunk.BLOCKS_Z;
	
	/*
	 * Each cube currently consists of 36 vertices ( 6 faces with 2 triangles each ), could be reduced
	 * by using triangle strips.
	 */	
	@SuppressWarnings("unused")
	private static final int WORST_CASE_VERTEX_COUNT = WORST_CASE_CUBE_COUNT*36;
	
	private final ShortArrayBuilder indexBuilder = new ShortArrayBuilder(10000,10000);
	private final FloatArrayBuilder vertexBuilder = new FloatArrayBuilder(10000*ELEMENTS_PER_VERTEX,1000*ELEMENTS_PER_VERTEX);

	private VertexBufferObject vbo;
	private IndexBufferObject ibo;

	private int vertexCount = 0;
	
	public BlockRenderer begin() 
	{
		indexBuilder.begin();
		vertexBuilder.begin();
		vertexCount = 0; 
		return this;
	}

	public void addBlock(float centerX,float centerY,float centerZ, float halfBlockSize, Color color ,int sideMask ) 
	{
		if ( (vertexCount+24) > 65535 ) {
			System.out.println("Too many vertices!");
			return;
		}
		
		int p0,p1,p2,p3;
		if ( (sideMask & SIDE_FRONT ) != 0 ) {

			p0 = vertexCount;
			p1 = vertexCount+1;
			p2 = vertexCount+2;
			p3 = vertexCount+3;
			vertexCount += 4;

			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					color.r,color.g,color.b, color.a ); // color RED

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					color.r,color.g,color.b, color.a ); // color GREEN

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					color.r,color.g,color.b, color.a ); // color BLUE

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,0,1, // normal
					color.r,color.g,color.b, color.a ); // color WHITE

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
					color.r,color.g,color.b, color.a ); // color RED

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					0,0,-1, // normal
					color.r,color.g,color.b, color.a ); // color GREEN

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					0,0,-1, // normal
					color.r,color.g,color.b, color.a ); // color BLUE

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					0,0,-1, // normal
					color.r,color.g,color.b, color.a ); // color WHITE

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
					color.r,color.g,color.b, color.a ); // color RED

			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					-1,0,0, // normal
					color.r,color.g,color.b, color.a ); // color GREEN

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					-1,0,0, // normal
					color.r,color.g,color.b, color.a ); // color BLUE

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					-1,0,0, // normal
					color.r,color.g,color.b, color.a ); // color WHITE

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
					color.r,color.g,color.b, color.a ); // color RED

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					1,0,0, // normal
					color.r,color.g,color.b, color.a ); // color GREEN

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					1,0,0, // normal
					color.r,color.g,color.b, color.a ); // color BLUE

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					1,0,0, // normal
					color.r,color.g,color.b, color.a ); // color WHITE

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
					color.r,color.g,color.b, color.a ); // color RED

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ - halfBlockSize , // position
					0,1,0, // normal
					color.r,color.g,color.b, color.a ); // color GREEN

			vertexBuilder.put( centerX+halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,1,0, // normal
					color.r,color.g,color.b, color.a ); // color BLUE

			vertexBuilder.put( centerX-halfBlockSize , centerY + halfBlockSize ,centerZ + halfBlockSize , // position
					0,1,0, // normal
					color.r,color.g,color.b, color.a ); // color WHITE

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
					color.r,color.g,color.b, color.a ); // color RED

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ - halfBlockSize , // position
					0,-1,0, // normal
					color.r,color.g,color.b, color.a ); // color GREEN

			vertexBuilder.put( centerX+halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,-1,0, // normal
					color.r,color.g,color.b, color.a ); // color BLUE

			vertexBuilder.put( centerX-halfBlockSize , centerY - halfBlockSize ,centerZ + halfBlockSize , // position
					0,-1,0, // normal
					color.r,color.g,color.b, color.a ); // color WHITE

			indexBuilder.put( (short) p3,(short) p0, (short) p1 , (short) p3 , (short) p1 , (short) p2 );
		}			
	}

	private VertexBufferObject createVBO(int vertexCount) 
	{
		final VertexAttribute[] attrs = new VertexAttribute[] 
				{
				new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
				new VertexAttribute(Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
				new VertexAttribute(Usage.Color, 4, ShaderProgram.COLOR_ATTRIBUTE) // (r,g,b,a)
				};
		return new VertexBufferObject(true, vertexCount , attrs );		
	}	

	public BlockRenderer end() 
	{
		indexBuilder.end();
		vertexBuilder.end();		
		return this;
	}

	public void render(ShaderProgram shader) 
	{
		final int vertexCount = vertexBuilder.actualSize() / ELEMENTS_PER_VERTEX;
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
		}

		if ( ibo == null ||  ibo.getNumMaxIndices() < indexBuilder.actualSize()  ) 
		{
			if ( ibo != null ) {
				ibo.dispose();
				ibo = null;
			}
			if ( DEBUG_PERFORMANCE ) {
				System.out.println("Creating VBO for "+indexBuilder.actualSize()+" indices.");
			}
			ibo = createIBO( indexBuilder.actualSize() );
		}		

		vbo.setVertices( vertexBuilder.array , 0 , vertexBuilder.actualSize() );
		ibo.setIndices( indexBuilder.array , 0 , indexBuilder.actualSize() );

		vbo.bind( shader );
		ibo.bind();

		if ( CULL_FACES ) {
			Gdx.graphics.getGL20().glEnable( GL20.GL_CULL_FACE );
		}
		if ( DEPTH_BUFFER ) {
			Gdx.graphics.getGL20().glEnable(GL11.GL_DEPTH_TEST);
		}

		Gdx.graphics.getGL20().glDrawElements(GL20.GL_TRIANGLES, indexBuilder.actualSize() , GL20.GL_UNSIGNED_SHORT , 0);

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
		return new IndexBufferObject(true,indexCount); // 1 triangle
	}		

	@Override
	public void dispose() 
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
}