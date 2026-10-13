package com.example.lifeplanner.desktop.ui;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

/** Halka grafik: ortada başlık ve alt metin. */
public class Donut extends StackPane {

	public record Slice(double value, String color) {
	}

	public Donut(List<Slice> slices, double size, double thickness, String title, String subtitle) {
		double r = (size - thickness) / 2;
		var track = new Circle(r, Color.TRANSPARENT);
		track.setStroke(Color.web("#1c202b"));
		track.setStrokeWidth(thickness);

		var arcs = new javafx.scene.Group(track);
		double total = slices.stream().mapToDouble(Slice::value).sum();
		double gap = slices.size() > 1 ? 1.2 : 0;
		double start = 90; // saat 12'den başla, saat yönünde
		for (var s : slices) {
			double sweep = total > 0 ? s.value() / total * 360 : 0;
			var arc = new Arc(0, 0, r, r, start - gap / 2, -(sweep - gap));
			arc.setType(ArcType.OPEN);
			arc.setFill(Color.TRANSPARENT);
			arc.setStroke(Color.web(s.color()));
			arc.setStrokeWidth(thickness);
			arc.setStrokeLineCap(StrokeLineCap.BUTT);
			arc.setEffect(new DropShadow(10, Color.web(s.color(), 0.45)));
			arcs.getChildren().add(arc);
			start -= sweep;
		}
		var center = new VBox(2, Ui.label(title, "donut-title"), Ui.label(subtitle, "muted"));
		center.setAlignment(Pos.CENTER);
		getChildren().addAll(arcs, center);
		setMinSize(size, size);
		setMaxSize(size, size);
	}
}
