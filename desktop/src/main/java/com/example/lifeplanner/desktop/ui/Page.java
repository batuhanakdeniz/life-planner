package com.example.lifeplanner.desktop.ui;

import javafx.scene.layout.VBox;

/** Ana penceredeki bir sayfa. Her gösterildiğinde {@link #refresh()} çağrılır. */
public abstract class Page extends VBox {

	protected Page() {
		super(16);
		getStyleClass().add("page");
	}

	public abstract void refresh();
}
