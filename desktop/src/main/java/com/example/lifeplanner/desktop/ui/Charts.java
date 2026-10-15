package com.example.lifeplanner.desktop.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.DoubleFunction;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Tuvale çizilen küçük grafikler; genişlik değiştikçe yeniden çizilir (yazılar her boyutta aynı kalır). */
public final class Charts {

	private static final Color GRID = Color.web("#262b38");
	private static final Color LABEL = Color.web("#8a93a8");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Ui.TR);

	private Charts() {
	}

	public enum Kind { LINE, BAR }

	/** Genişliğe uyan, yüksekliği sabit bir tuval. */
	private abstract static class ChartPane extends Pane {

		private final Canvas canvas = new Canvas();

		ChartPane(double height) {
			getChildren().add(canvas);
			setMinHeight(height);
			setPrefHeight(height);
			setMaxHeight(height);
			setMinWidth(120);
		}

		@Override
		protected void layoutChildren() {
			canvas.setWidth(getWidth());
			canvas.setHeight(getHeight());
			var g = canvas.getGraphicsContext2D();
			g.clearRect(0, 0, getWidth(), getHeight());
			draw(g, getWidth(), getHeight());
		}

		abstract void draw(javafx.scene.canvas.GraphicsContext g, double w, double h);
	}

	public static Pane sparkline(List<Long> values, Color color) {
		return new ChartPane(40) {
			@Override
			void draw(javafx.scene.canvas.GraphicsContext g, double w, double h) {
				if (values == null || values.size() < 2) {
					return;
				}
				long min = values.stream().mapToLong(Long::longValue).min().orElse(0);
				long max = values.stream().mapToLong(Long::longValue).max().orElse(1);
				double span = Math.max(max - min, 1);
				g.setStroke(color);
				g.setLineWidth(2);
				g.beginPath();
				for (int i = 0; i < values.size(); i++) {
					double x = i * (w - 2) / (values.size() - 1) + 1;
					double y = h - 4 - (values.get(i) - min) / span * (h - 8);
					if (i == 0) {
						g.moveTo(x, y);
					}
					else {
						g.lineTo(x, y);
					}
				}
				g.stroke();
			}
		};
	}

	/** Eksenli çizgi ya da sütun grafik. dates ile values aynı uzunlukta olmalı. */
	public static Pane chart(Kind kind, List<LocalDate> dates, List<Double> values, Color color, DoubleFunction<String> format) {
		return new ChartPane(170) {
			@Override
			void draw(javafx.scene.canvas.GraphicsContext g, double w, double h) {
				if (values.isEmpty()) {
					return;
				}
				double left = 52, right = 8, top = 10, bottom = 22;
				double iw = w - left - right, ih = h - top - bottom;
				double max = Math.max(values.stream().mapToDouble(Double::doubleValue).max().orElse(1), 1);
				g.setFont(Font.font(11));
				g.setTextBaseline(VPos.CENTER);
				// Yatay çizgiler ve değer etiketleri
				for (double f : new double[] { 0, 0.5, 1 }) {
					double y = top + ih * (1 - f);
					g.setStroke(GRID);
					g.setLineWidth(1);
					g.strokeLine(left, y, w - right, y);
					g.setFill(LABEL);
					g.setTextAlign(TextAlignment.RIGHT);
					g.fillText(format.apply(max * f), left - 6, y);
				}
				// Tarih etiketleri
				g.setTextBaseline(VPos.BOTTOM);
				g.setTextAlign(TextAlignment.LEFT);
				g.fillText(dates.get(0).format(DAY), left, h - 2);
				g.setTextAlign(TextAlignment.RIGHT);
				g.fillText(dates.get(dates.size() - 1).format(DAY), w - right, h - 2);

				int n = values.size();
				if (kind == Kind.BAR) {
					double bw = Math.max(iw / n - 3, 2);
					g.setFill(color);
					for (int i = 0; i < n; i++) {
						double x = left + (n == 1 ? iw / 2 : i * iw / (n - 1));
						double bh = Math.max(values.get(i) / max * ih, 1);
						g.fillRoundRect(x - bw / 2, top + ih - bh, bw, bh, 3, 3);
					}
					return;
				}
				double[] xs = new double[n + 2];
				double[] ys = new double[n + 2];
				for (int i = 0; i < n; i++) {
					xs[i] = left + (n == 1 ? iw / 2 : i * iw / (n - 1));
					ys[i] = top + ih - values.get(i) / max * ih;
				}
				// Alan dolgusu
				xs[n] = xs[n - 1];
				ys[n] = top + ih;
				xs[n + 1] = xs[0];
				ys[n + 1] = top + ih;
				g.setFill(color.deriveColor(0, 1, 1, 0.14));
				g.fillPolygon(xs, ys, n + 2);
				g.setStroke(color);
				g.setLineWidth(2.5);
				g.strokePolyline(xs, ys, n);
				g.setFill(color);
				g.fillOval(xs[n - 1] - 4, ys[n - 1] - 4, 8, 8);
			}
		};
	}
}
