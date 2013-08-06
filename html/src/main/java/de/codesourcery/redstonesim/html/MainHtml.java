package de.codesourcery.redstonesim.html;

import de.codesourcery.redstonesim.core.Main;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;

public class MainHtml extends GwtApplication {
	@Override
	public ApplicationListener getApplicationListener () {
		return new Main();
	}
	
	@Override
	public GwtApplicationConfiguration getConfig () {
		return new GwtApplicationConfiguration(480, 320);
	}
}
