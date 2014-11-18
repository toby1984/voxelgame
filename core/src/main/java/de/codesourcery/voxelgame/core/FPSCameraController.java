package de.codesourcery.voxelgame.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public abstract class FPSCameraController extends InputAdapter
{
	private boolean strafePressed = false;
	private boolean strafeLeft=false;

	private int lastX=-1;
	private int lastY=-1;

	/** The angle to rotate when moved the full width or height of the screen. */
	public float rotateAngle = 360f;

	/** The units to translate the camera when moved the full width or height of the screen. */
	public float translateUnits = 200;

	/** The target to rotate around. */
	public final Vector3 target = new Vector3();

	public int forwardKey = Keys.W;
	protected boolean forwardPressed;

	public int backwardKey = Keys.S;
	protected boolean backwardPressed;

	/** The camera. */
	public Camera camera;

	private final Vector3 tmpV1 = new Vector3();

	private final Matrix4 TMP_MATRIX = new Matrix4().idt();
	public final Matrix3 normalMatrix = new Matrix3().idt();

	public final Vector3 sunPosition = new Vector3();

	public FPSCameraController(final Camera camera,Vector3 initialDirection)
	{
		this.camera = camera;
		updateNormalMatrix();
		updateSunPosition();
	}

	private void updateNormalMatrix() {
		TMP_MATRIX.set( camera.view ).inv().tra();
		normalMatrix.set( TMP_MATRIX );
	}

	private void updateSunPosition()
	{
		this.sunPosition.set( camera.position.x , 5000 , camera.position.z );
	}

	public void update()
	{
		if ( forwardPressed || backwardPressed || strafePressed)
		{
			final float delta = Gdx.graphics.getDeltaTime();

			boolean updateRequired = false;
			if ( strafePressed )
			{
				final float deltaX = strafeLeft ? -2f : 2f;
				updateRequired |= translateCamera(tmpV1.set(camera.direction).crs(camera.up).nor().scl(deltaX *delta* translateUnits));
			}

			if (forwardPressed) {
				updateRequired |= translateCamera(tmpV1.set(camera.direction).scl(delta * translateUnits));
			}
			else if (backwardPressed)
			{
				updateRequired |= translateCamera(tmpV1.set(camera.direction).scl(-delta * translateUnits));
			}
			if ( updateRequired )
			{
				updateCamera();
				cameraTranslated(this.camera);
			}
		}
	}

	private void updateCamera()
	{
		camera.update();
		updateNormalMatrix();
		updateSunPosition();
	}

	public boolean translateCamera(Vector3 v)
	{
		if ( canTranslateCamera( this.camera , v ) ) {
			camera.translate(v);
			target.add(v);
			return true;
		}
		return false;
	}

	public abstract boolean canTranslateCamera(Camera cam,Vector3 posDelta);

	@Override
	public boolean keyDown (int keycode)
	{
		if ( isStrafeKey(keycode ) )
		{
			strafePressed = true;
			strafeLeft = isStrafeLeftKey( keycode );
			return false;
		}

		if (keycode == forwardKey) {
			forwardPressed = true;
		} else if (keycode == backwardKey) {
			backwardPressed = true;
		}
		return false;
	}

	@Override
	public boolean keyUp (int keycode)
	{
		if ( keycode == Input.Keys.ESCAPE) {
			Gdx.input.setCursorCatched(false);
			return false;
		}

		if( keycode == Input.Keys.BACKSPACE ) {
			toggleWireframe();
			return false;
		}

		if ( isStrafeKey(keycode ) ) {
			strafePressed = false;
		}
		else if (keycode == forwardKey)
		{
			forwardPressed = false;
		}
		else if (keycode == backwardKey)
		{
			backwardPressed = false;
		}
		return false;
	}

	@Override
	public boolean mouseMoved (int screenX, int screenY)
	{
		if ( ! Gdx.input.isCursorCatched() ) {
			lastX = lastY = -1;
			return false;
		}

		if ( lastX == -1 || lastY == -1 ) {
			lastX = screenX;
			lastY = screenY;
			return false;
		}

		final float deltaX = (screenX - lastX) / (float) Gdx.graphics.getWidth();
		final float deltaY = (lastY - screenY) / (float) Gdx.graphics.getHeight();

		tmpV1.set(camera.direction).crs(camera.up).y = 0f;

		// rotation around X axis
		final float rotXAxis = deltaY * rotateAngle;
		camera.rotateAround(camera.position, tmpV1.nor(), rotXAxis);

		// rotation around Y axis
		final float rotYAxis = deltaX * -rotateAngle;
		camera.rotateAround(camera.position, Vector3.Y, rotYAxis);

		lastX = screenX;
		lastY = screenY;

		updateCamera();
		cameraRotated(this.camera);
		return false;
	}

	@Override
	public boolean touchDown (int screenX, int screenY, int pointer, int button)
	{
		if ( ! Gdx.input.isCursorCatched() ) {
			Gdx.input.setCursorCatched(true);
			return false;
		}

		if ( button == Input.Buttons.LEFT )
		{
			onLeftClick();
		}
		else if ( button == Input.Buttons.RIGHT ) {
			onRightClick();
		}
		return false;
	}

	public abstract void cameraTranslated(Camera camera);

	public abstract void cameraRotated(Camera camera);

	public abstract void toggleWireframe();

	public abstract void onLeftClick();

	public abstract void onRightClick();

	private boolean isStrafeLeftKey(int keyCode) { return keyCode == Input.Keys.A; }

	private boolean isStrafeRightKey(int keyCode) { return keyCode == Input.Keys.D; }

	private boolean isStrafeKey(int keyCode)
	{
		return isStrafeLeftKey(keyCode) || isStrafeRightKey(keyCode);
	}
}