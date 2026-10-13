package com.example.lifeplanner.desktop.ui;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Bulletin;
import com.example.lifeplanner.desktop.api.Models.BulletinChannel;
import com.example.lifeplanner.desktop.api.Models.BulletinVideo;
import com.example.lifeplanner.desktop.api.Models.ChannelDigest;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Günlük YouTube bülteni — gazete tarzı: üstte günün özeti, altında katlanır kanal kartları. */
public class BulletinPage extends Page {

	private static final ZoneId TR = ZoneId.of("Europe/Istanbul");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Ui.TR).withZone(TR);

	private final ApiClient api;
	private final VBox body = new VBox(14);
	private final ComboBox<LocalDate> datePicker = new ComboBox<>();
	private final Label channelCount = new Label();
	private List<BulletinChannel> channels = List.of();
	private boolean showChannels;
	private final Set<Integer> openChannels = new HashSet<>();
	private final Set<String> openVideos = new HashSet<>();

	public BulletinPage(ApiClient api) {
		this.api = api;
		datePicker.setConverter(TaskWidgets.converter(d -> d == null ? "" : Ui.cap(d.format(DateTimeFormatter.ofPattern("d MMMM EEE", Ui.TR)))));
		datePicker.setOnAction(e -> {
			if (datePicker.getValue() != null) {
				openChannels.clear();
				load(datePicker.getValue());
			}
		});
		var older = Ui.button("‹", () -> step(1));
		var newer = Ui.button("›", () -> step(-1));
		var channelsBtn = Ui.button("Kanallar", () -> {
			showChannels = !showChannels;
			load(datePicker.getValue());
		});
		channelsBtn.graphicProperty().set(channelCount);
		channelsBtn.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
		getChildren().addAll(
				Ui.row(Ui.label("📰 Bülten", "h1"), Ui.spacer(), older, datePicker, newer, channelsBtn),
				Ui.label("Takip ettiğin YouTube kanallarının günlük özeti. Her gün 12:05'te hazırlanır.", "subtitle"),
				body);
	}

	private void step(int delta) {
		var items = datePicker.getItems();
		int i = items.indexOf(datePicker.getValue()) + delta;
		if (i >= 0 && i < items.size()) {
			datePicker.setValue(items.get(i));
			openChannels.clear();
			load(items.get(i));
		}
	}

	@Override
	public void refresh() {
		Ui.async(() -> new Object[] { api.bulletinDates(), api.bulletinChannels() }, r -> {
			@SuppressWarnings("unchecked") var dates = (List<LocalDate>) r[0];
			@SuppressWarnings("unchecked") var chs = (List<BulletinChannel>) r[1];
			channels = chs;
			channelCount.setText("(" + chs.stream().filter(BulletinChannel::active).count() + ")");
			var current = datePicker.getValue();
			datePicker.getItems().setAll(dates);
			datePicker.setValue(current != null && dates.contains(current) ? current : dates.isEmpty() ? null : dates.get(0));
			load(datePicker.getValue());
		});
	}

	private void load(LocalDate date) {
		if (date == null) {
			render(null);
			return;
		}
		Ui.async(() -> java.util.Optional.ofNullable(api.bulletin(date)), b -> render(b.orElse(null)));
	}

	private void render(Bulletin b) {
		body.getChildren().clear();
		// Ekran görüntüsü modunda açık kart görünümü de görünsün
		if (System.getProperty("lifeplanner.snapshot") != null && b != null && !b.channels().isEmpty() && openChannels.isEmpty()) {
			openChannels.add(0);
			b.channels().get(0).videos().stream().findFirst().ifPresent(v -> openVideos.add(v.videoId()));
		}
		if (showChannels) {
			body.getChildren().add(channelList());
		}
		if (b == null) {
			var card = Ui.card(Ui.label(channels.isEmpty() ? "Takip edilecek kanal yok" : "Henüz bülten yok", "h2"),
					Ui.label(channels.isEmpty()
							? "Obsidian'da Bülten/Kanallar.md tablosuna kanal ekle (ör. @kanaladi). En geç 5 dakika içinde buraya gelir."
							: "İlk bülten bir sonraki 12:05 çalışmasında hazırlanacak.", "muted"));
			body.getChildren().add(card);
			return;
		}
		body.getChildren().add(digest(b));
		for (int i = 0; i < b.channels().size(); i++) {
			body.getChildren().add(channelCard(b.channels().get(i), i));
		}
	}

	private Node channelList() {
		var card = Ui.card(Ui.row(Ui.label("Takip edilen kanallar", "h2"), Ui.spacer(),
				Ui.label("Düzenlemek için: Obsidian → Bülten/Kanallar.md ya da bulletin_channels tablosu", "muted")));
		if (channels.isEmpty()) {
			card.getChildren().add(Ui.empty("Henüz kanal yok"));
		}
		for (var c : channels) {
			var link = new Hyperlink(c.handle());
			link.setOnAction(e -> Ui.openUrl(c.url()));
			var row = Ui.row(Ui.label(c.name()), link, Ui.spacer(),
					Ui.tag(c.cutoffTime().toString().substring(0, 5) + " kesim · " + c.lookbackHours() + " saat geriye", null));
			if (!c.active()) {
				row.getChildren().add(Ui.tag("pasif", null));
				row.getStyleClass().add("done");
			}
			row.getStyleClass().add("item");
			card.getChildren().add(row);
		}
		return card;
	}

	private Node digest(Bulletin b) {
		int total = b.channels().stream().mapToInt(c -> c.videos().size()).sum();
		var card = Ui.card(Ui.row(Ui.label("Günün özeti", "h2"), Ui.spacer(),
				Ui.label(Ui.cap(b.date().format(Ui.FULL)) + " · " + b.channels().size() + " kanal · " + total + " video", "muted")));
		card.getStyleClass().add("digest");
		if (b.overview() != null) {
			card.getChildren().add(wrap(b.overview(), "overview"));
		}
		for (int i = 0; i < b.channels().size(); i++) {
			var c = b.channels().get(i);
			int index = i;
			var name = new Hyperlink(c.channelName());
			name.getStyleClass().add("digest-name");
			name.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // uzun özet adı kırpmasın
			name.setOnAction(e -> {
				openChannels.add(index);
				render(b);
			});
			var text = c.error() != null ? wrap(c.error(), "neg")
					: c.videos().isEmpty() ? wrap("Bu aralıkta video yok.", "muted") : wrap(c.shortSummary() != null ? c.shortSummary() : "", null);
			HBox.setHgrow(text, Priority.ALWAYS);
			var row = new HBox(6, name, text);
			card.getChildren().add(row);
		}
		return card;
	}

	private Node channelCard(ChannelDigest c, int index) {
		boolean open = openChannels.contains(index);
		var countText = c.videos().size() + " video" + (c.skippedVideos() > 0 ? " · ≈" + c.skippedVideos() + " daha" : "");
		var head = Ui.row(Ui.label(c.channelName(), "h2"), Ui.label(c.channelHandle(), "muted"), Ui.spacer(),
				Ui.label(TIME.format(c.windowStart()) + " → " + TIME.format(c.windowEnd()), "muted"), Ui.tag(countText, null),
				Ui.label(open ? "▾" : "▸", "muted"));
		head.getStyleClass().add("clickable");
		var card = Ui.card(head);
		head.setOnMouseClicked(e -> {
			if (!openChannels.remove(index)) {
				openChannels.add(index);
			}
			card.getChildren().setAll(((VBox) channelCard(c, index)).getChildren());
		});
		if (!open) {
			return card;
		}
		if (c.shortSummary() != null) {
			card.getChildren().add(wrap(c.shortSummary(), "muted"));
		}
		if (c.skippedVideos() > 0) {
			card.getChildren().add(wrap("≈" + c.skippedVideos() + " video daha yayınlandı; kanal başına günlük sınır nedeniyle özetlenmedi.", "warn"));
		}
		if (c.videos().isEmpty()) {
			card.getChildren().add(Ui.empty("Bu aralıkta video yok."));
		}
		c.videos().forEach(v -> card.getChildren().add(video(v)));
		return card;
	}

	private Node video(BulletinVideo v) {
		boolean open = openVideos.contains(v.videoId());
		var box = new VBox(6);
		box.getStyleClass().add("video");

		var title = wrap(v.title(), "video-title");
		HBox.setHgrow(title, Priority.ALWAYS);
		var head = Ui.row(Ui.label(open ? "▾" : "▸", "muted"), title);
		if (v.durationSeconds() != null) {
			head.getChildren().add(Ui.tag(Math.round(v.durationSeconds() / 60.0) + " dk", null));
		}
		if (!v.hasTranscript()) {
			head.getChildren().add(Ui.tag("altyazı yok", "overdue"));
		}
		head.getStyleClass().add("clickable");
		var yt = new Hyperlink("YouTube'da aç ↗");
		yt.setOnAction(e -> Ui.openUrl(v.url()));
		var sub = Ui.row(Ui.label(v.publishedAt() != null ? TIME.format(v.publishedAt()) : "", "muted"), yt);
		sub.setPadding(new Insets(0, 0, 0, 20));
		box.getChildren().addAll(head, sub);
		if (v.summary() != null) {
			var s = wrap(v.summary(), null);
			VBox.setMargin(s, new Insets(0, 0, 0, 20));
			box.getChildren().add(s);
		}
		var detail = new VBox(6);
		detail.setPadding(new Insets(0, 0, 0, 20));
		if (open) {
			if (!v.keyPoints().isEmpty()) {
				detail.getChildren().add(Ui.label("Önemli noktalar", "muted"));
				v.keyPoints().forEach(p -> detail.getChildren().add(wrap("•  " + p, null)));
			}
			if (!v.chapters().isEmpty()) {
				detail.getChildren().add(Ui.label("Bölümler", "muted"));
				v.chapters().forEach(ch -> {
					var ts = new Hyperlink(clock(ch.seconds()));
					ts.setOnAction(e -> Ui.openUrl(v.url() + "&t=" + ch.seconds() + "s"));
					detail.getChildren().add(Ui.row(ts, wrap(ch.label(), null)));
				});
			}
			var m = v.mentions();
			if (m != null) {
				addMentions(detail, "Kişiler", m.people());
				addMentions(detail, "Şirketler / kurumlar", m.companies());
				addMentions(detail, "Rakamlar", m.numbers());
			}
		}
		else if (!v.keyPoints().isEmpty() || !v.chapters().isEmpty()) {
			var more = new Hyperlink("Detayları göster (" + v.keyPoints().size() + " nokta, " + v.chapters().size() + " bölüm)");
			more.setOnAction(e -> toggleVideo(v, box));
			detail.getChildren().add(more);
		}
		box.getChildren().add(detail);
		head.setOnMouseClicked(e -> toggleVideo(v, box));
		return box;
	}

	private void toggleVideo(BulletinVideo v, VBox box) {
		if (!openVideos.remove(v.videoId())) {
			openVideos.add(v.videoId());
		}
		box.getChildren().setAll(((VBox) video(v)).getChildren());
	}

	private static void addMentions(VBox parent, String label, List<String> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		var flow = new FlowPane(6, 6);
		flow.getChildren().add(Ui.label(label + ":", "muted"));
		items.forEach(x -> flow.getChildren().add(Ui.tag(x, null)));
		parent.getChildren().add(flow);
	}

	private static Label wrap(String text, String styleClass) {
		var l = styleClass != null ? Ui.label(text, styleClass) : Ui.label(text);
		l.setWrapText(true);
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}

	static String clock(int s) {
		int h = s / 3600, m = s % 3600 / 60, sec = s % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
	}
}
