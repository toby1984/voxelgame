package de.codesourcery.voxelgame.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelgame.core.FPSCameraController;

public class Bullet implements ITickListener {

	private Mesh mesh;
	
	private final Vector3 currentPosition=new Vector3();
	private final Vector3 initialPosition=new Vector3();
	private final Vector3 direction=new Vector3();	
	
	private Matrix4 mvp = new Matrix4().idt();
	
	private int tickCount = 0;
	private final ShaderProgram shader;
	
	public Bullet(ShaderProgram shader,Vector3 position,Vector3 direction) 
	{
		this.initialPosition.set(position);
		this.currentPosition.set(position);
		this.direction.set( direction ).scl( 2f);
		final Vector3 tmp = new Vector3( direction ).scl( 50 );
		this.currentPosition.add( tmp );
		this.shader = shader;
	}
	
	@Override
	public void initialize() {
		mesh = createMesh();
	}
	
	private Mesh createMesh() {
		
		final MeshBuilder builder = new MeshBuilder();
		builder.begin( MeshBuilder.createAttributes( Usage.Position | Usage.Color ) , GL20.GL_TRIANGLES );
		 builder.setColor( Color.ORANGE );
		builder.sphere( Chunk.BLOCK_WIDTH ,Chunk.BLOCK_HEIGHT,Chunk.BLOCK_DEPTH , 6 , 6 );
//		builder.sphere( 5 ,5 ,5 ,12 ,12 );
		return builder.end();
	}

	@Override
	public boolean tick(FPSCameraController cameraController) 
	{
		if ( initialPosition.dst2( currentPosition ) > 600*600 ) {
			return false;
		}
		
		currentPosition.add( direction  );
		
		mvp.set( cameraController.camera.combined );
		mvp.translate( currentPosition );
		
		Gdx.graphics.getGL20().glEnable( GL20.GL_CULL_FACE );
		Gdx.graphics.getGL20().glEnable(GL11.GL_DEPTH_TEST);
		
		shader.begin();
		
		shader.setUniformMatrix("mvp", mvp );
		
		mesh.render( shader , GL20.GL_TRIANGLES );
		
		shader.end();
		
		Gdx.graphics.getGL20().glDisable( GL20.GL_CULL_FACE );
		Gdx.graphics.getGL20().glDisable( GL11.GL_DEPTH_TEST);		
		
		return true;
	}

	@Override
	public void dispose() 
	{
		if ( mesh != null ) {
			mesh.dispose();
		}
	}

}
