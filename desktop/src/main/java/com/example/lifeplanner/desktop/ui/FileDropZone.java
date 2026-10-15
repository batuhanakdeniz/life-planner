package com.example.lifeplanner.desktop.ui;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Ortak dosya bırakma / seçme alanı. Yüklemeyi kendisi yapmaz; seçilen dosyayı onFile'a verir. Örnek:
 * {@code new FileDropZone("Hesap özeti PDF'ini bırak", List.of("pdf"), file -> ...)}
 */
public class FileDropZone extends VBox {

	private final Label title;
	private final Label hint;
	private final String idleText;
	private boolean busy;

	public FileDropZone(String text, List<String> extensions, Consumer<File> onFile) {
		super(4);
		this.idleText = text;
		getStyleClass().add("file-drop");
		setAlignment(Pos.CENTER);
		title = Ui.label(text, "h2");
		hint = Ui.label("ya da tıklayıp seç (" + String.join(", ", extensions.stream().map(e -> "." + e).toList()) + ")", "muted");
		getChildren().addAll(Ui.label("📄", "file-drop-icon"), title, hint);

		setOnMouseClicked(e -> {
			if (busy) {
				return;
			}
			var chooser = new FileChooser();
			chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Dosyalar",
					extensions.stream().map(x -> "*." + x).toList()));
			var f = chooser.showOpenDialog(getScene().getWindow());
			if (f != null) {
				onFile.accept(f);
			}
		});
		setOnDragOver(e -> {
			if (!busy && e.getDragboard().hasFiles()) {
				e.acceptTransferModes(TransferMode.COPY);
				if (!getStyleClass().contains("over")) {
					getStyleClass().add("over");
				}
			}
			e.consume();
		});
		setOnDragExited(e -> getStyleClass().remove("over"));
		setOnDragDropped(e -> {
			var files = e.getDragboard().getFiles();
			e.setDropCompleted(!files.isEmpty());
			e.consume();
			if (files.isEmpty() || busy) {
				return;
			}
			var f = files.get(0);
			var name = f.getName().toLowerCase(Locale.ROOT);
			if (extensions.stream().noneMatch(x -> name.endsWith("." + x))) {
				Ui.error(new IllegalArgumentException("Yalnızca " + String.join(", ", extensions) + " dosyaları yüklenebilir"));
				return;
			}
			onFile.accept(f);
		});
	}

	public void setBusy(boolean busy) {
		this.busy = busy;
		title.setText(busy ? "Yükleniyor ve okunuyor…" : idleText);
		hint.setVisible(!busy);
		setOpacity(busy ? 0.75 : 1);
	}
}
