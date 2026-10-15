package com.example.lifeplanner.desktop.ui;

import static com.example.lifeplanner.desktop.api.ApiClient.body;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Asset;
import com.example.lifeplanner.desktop.api.Models.Holding;
import com.example.lifeplanner.desktop.api.Models.Payment;
import com.example.lifeplanner.desktop.api.Models.PaymentsOverview;
import com.example.lifeplanner.desktop.api.Models.PriceStatus;
import com.example.lifeplanner.desktop.api.Models.Portfolio;
import com.example.lifeplanner.desktop.api.Models.Rate;
import com.example.lifeplanner.desktop.api.Models.Trade;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Finans: portföy (TL/USD), alım-satım işlemleri ve düzenli ödemeler. */
public class FinancePage extends Page {

	private enum View { PORTFOLIO, TRADES, PAYMENTS }

	/** Yeni varlık eklerken hızlı seçimler: sembol, ad, tür, fiyat birimi, birim, etiket. */
	private static final List<String[]> PRESETS = List.of(
			new String[] { "GRAM_ALTIN", "Gram altın", "GOLD", "TRY", "gr", "Gram altın" },
			new String[] { "CEYREK_ALTIN", "Çeyrek altın", "GOLD", "TRY", "adet", "Çeyrek altın" },
			new String[] { "ONS_ALTIN", "Ons altın", "GOLD", "USD", "ons", "Ons altın" },
			new String[] { "GUMUS", "Gram gümüş", "GOLD", "TRY", "gr", "Gram gümüş" },
			new String[] { "USD", "Amerikan doları", "CURRENCY", "TRY", "$", "USD" },
			new String[] { "EUR", "Euro", "CURRENCY", "TRY", "€", "EUR" },
			new String[] { "", "", "STOCK", "TRY", "lot", "BIST hissesi" },
			new String[] { "", "", "STOCK", "USD", "adet", "ABD hissesi" },
			new String[] { "", "", "FUND", "TRY", "pay", "Yatırım fonu" });

	private final ApiClient api;
	private final VBox body = new VBox(16);
	private final List<ToggleButton> viewButtons = new java.util.ArrayList<>();
	private StatementsPane statementsPane;
	/** Ödemeler sekmesinde hesap özetleri alt sekmesi mi açık. */
	private boolean statementsTab;
	/**
	 * Her yenilemede artar. Sekme hızla değiştirildiğinde eski isteğin geç gelen yanıtı yeni sekmenin
	 * içeriğinin üzerine yazmasın diye yalnızca en son isteğin yanıtı çizilir.
	 */
	private int generation;
	private View view = View.PORTFOLIO;
	private Long tradeFilter;
	private Trade editingTrade;

	public FinancePage(ApiClient api) {
		this.api = api;
		var group = new ToggleGroup();
		var bar = new HBox();
		bar.getStyleClass().add("toggle-group-bar");
		for (var v : View.values()) {
			var b = new ToggleButton(switch (v) {
				case PORTFOLIO -> "◔ Portföy";
				case TRADES -> "⇄ İşlemler";
				case PAYMENTS -> "🗓 Ödemeler";
			});
			b.getStyleClass().add("segment");
			b.setToggleGroup(group);
			b.setSelected(v == view);
			b.setOnAction(e -> {
				b.setSelected(true);
				view = v;
				refresh();
			});
			viewButtons.add(b);
			bar.getChildren().add(b);
		}
		getChildren().addAll(Ui.row(Ui.label("💰 Finans", "h1"), Ui.spacer(), bar),
				Ui.label("Yatırımların TL ve dolar bazında, düzenli ödemelerin tek yerde.", "subtitle"), body);
	}

	/** 0 = Portföy, 1 = İşlemler, 2 = Ödemeler (ekran görüntüsü modu için). */
	public void showView(int index) {
		view = View.values()[index];
		viewButtons.get(index).setSelected(true);
		refresh();
	}

	@Override
	public void refresh() {
		int gen = ++generation;
		switch (view) {
			case PORTFOLIO -> Ui.async(() -> new Object[] { api.portfolio(), api.assets(), api.rates(), api.priceStatus() },
					r -> {
						if (gen == generation) {
							renderPortfolio((Portfolio) r[0], cast(r[1]), cast(r[2]), (PriceStatus) r[3]);
						}
					});
			case TRADES -> Ui.async(() -> new Object[] { api.assets(), api.trades(tradeFilter), api.rates() },
					r -> {
						if (gen == generation) {
							renderTrades(cast(r[0]), cast(r[1]), cast(r[2]));
						}
					});
			case PAYMENTS -> {
				if (statementsTab) {
					if (statementsPane == null) {
						statementsPane = new StatementsPane(api);
					}
					body.getChildren().setAll(paymentsTabs(), statementsPane);
					statementsPane.refresh();
				}
				else {
					Ui.async(api::payments, o -> {
						if (gen == generation) {
							renderPayments(o);
						}
					});
				}
			}
		}
	}

	/** Ödemeler sekmesinin "Düzenli / Hesap özetleri" alt sekmeleri. */
	private Node paymentsTabs() {
		var group = new ToggleGroup();
		var bar = new HBox();
		bar.getStyleClass().add("toggle-group-bar");
		bar.setMaxWidth(Region.USE_PREF_SIZE);
		for (var entry : List.of(Map.entry(false, "Düzenli"), Map.entry(true, "Hesap özetleri"))) {
			var b = new ToggleButton(entry.getValue());
			b.getStyleClass().add("segment");
			b.setToggleGroup(group);
			b.setSelected(entry.getKey() == statementsTab);
			b.setOnAction(e -> {
				b.setSelected(true);
				showPaymentsTab(entry.getKey());
			});
			bar.getChildren().add(b);
		}
		return bar;
	}

	/** Ödemeler sekmesinde alt sekme seçer (true = hesap özetleri). */
	public void showPaymentsTab(boolean statements) {
		statementsTab = statements;
		refresh();
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T> cast(Object o) {
		return (List<T>) o;
	}

	// =====================================================================
	// Portföy
	// =====================================================================
	private void renderPortfolio(Portfolio p, List<Asset> assets, List<Rate> rates, PriceStatus status) {
		var t = p.totals();
		var stats = new HBox(12,
				stat("Toplam değer", Ui.label(Money.money(t.valueTry(), "TRY", 0), "stat-value"), Ui.label(Money.money(t.valueUsd(), "USD", 0), "muted")),
				stat("Gerçekleşmemiş kâr/zarar",
						Ui.label(Money.signed(t.unrealizedTry(), "TRY", 0) + "  " + Money.pct(t.unrealizedPctTry()), "stat-value", "plain", Money.pnl(t.unrealizedTry())),
						Ui.label(Money.signed(t.unrealizedUsd(), "USD", 0) + "  " + Money.pct(t.unrealizedPctUsd()), Money.pnl(t.unrealizedUsd()))),
				stat("Gerçekleşen kâr/zarar",
						Ui.label(Money.signed(t.realizedTry(), "TRY", 0), "stat-value", "plain", Money.pnl(t.realizedTry())),
						Ui.label(Money.signed(t.realizedUsd(), "USD", 0), Money.pnl(t.realizedUsd()))),
				stat("Kurlar (değiştirmek için tıkla)", rateRow("USD", rates, p.rateIsFallback() ? p.usdTry() : null), rateRow("EUR", rates, null)));
		body.getChildren().setAll(statusBar(status), stats);

		var open = p.holdings().stream().filter(h -> !h.closed()).toList();
		if (open.isEmpty()) {
			var card = Ui.card(Ui.label("Portföyün boş", "h2"),
					Ui.label("Önce bir varlık ekle (ör. Gram altın), sonra \"İşlemler\" sekmesinden alımlarını gir.", "muted"),
					Ui.button("+ Varlık ekle", this::openAssetForm, "primary"));
			card.setAlignment(Pos.CENTER);
			body.getChildren().add(card);
		}
		else {
			// Dağılım: halka grafik + açıklama
			var slices = p.allocation().stream()
					.map(a -> new Donut.Slice(a.valueTry().doubleValue(), Money.ASSET_COLOR.get(a.type()))).toList();
			var donut = new Donut(slices, 200, 24, Money.money(t.valueTry(), "TRY", 0), Money.money(t.valueUsd(), "USD", 0));
			var legend = new VBox(2);
			legend.setMinWidth(300);
			p.allocation().forEach(a -> {
				var dot = colorDot(Money.ASSET_COLOR.get(a.type()));
				var row = Ui.row(dot, Ui.label(Money.ASSET_ICON.get(a.type()) + " " + Money.ASSET_TYPE.get(a.type())), Ui.spacer(),
						Ui.label(Money.money(a.valueTry(), "TRY", 0), "muted"), Ui.label("%" + Money.num(a.percent()), "h2"));
				row.getStyleClass().add("item");
				legend.getChildren().add(row);
			});
			var dist = new HBox(40, donut, legend);
			dist.setAlignment(Pos.CENTER_LEFT);
			body.getChildren().add(Ui.card(Ui.label("Dağılım", "h2"), dist));
			body.getChildren().add(holdingsTable(open, assets));
		}

		var closed = p.holdings().stream().filter(Holding::closed).toList();
		if (!closed.isEmpty()) {
			var card = Ui.card(Ui.label("Kapanmış pozisyonlar", "h2"));
			closed.forEach(h -> {
				var row = Ui.row(Ui.label(Money.ASSET_ICON.get(h.type()) + " " + h.name()), Ui.spacer(),
						Ui.label(Money.signed(h.realizedTry(), "TRY", 0), Money.pnl(h.realizedTry())),
						Ui.label(Money.signed(h.realizedUsd(), "USD", 0), Money.pnl(h.realizedUsd())));
				row.getStyleClass().add("item");
				card.getChildren().add(row);
			});
			body.getChildren().add(card);
		}

		var unused = assets.stream().filter(a -> !a.hasTrades()).toList();
		if (!unused.isEmpty()) {
			var card = Ui.card(Ui.label("İşlemi olmayan varlıklar", "h2"));
			unused.forEach(a -> {
				var row = Ui.row(Ui.label(Money.ASSET_ICON.get(a.type()) + " " + a.name()), Ui.label(a.symbol(), "muted"), Ui.spacer(),
						Ui.button("✕", () -> {
							if (Ui.confirm("\"" + a.name() + "\" silinsin mi?")) {
								Ui.run(() -> api.deleteAsset(a.id()), this::refresh);
							}
						}, "ghost", "del"));
				row.getStyleClass().add("item");
				card.getChildren().add(row);
			});
			body.getChildren().add(card);
		}
	}

	private VBox holdingsTable(List<Holding> open, List<Asset> assets) {
		var grid = new GridPane();
		grid.setHgap(14);
		grid.setVgap(0);
		String[] heads = { "Varlık", "Miktar", "Ort. maliyet", "Güncel fiyat", "Değer", "Kâr/zarar", "Ağırlık" };
		for (int i = 0; i < heads.length; i++) {
			var c = new ColumnConstraints();
			c.setHalignment(i == 0 ? HPos.LEFT : HPos.RIGHT);
			if (i == 0) {
				c.setHgrow(Priority.ALWAYS);
			}
			grid.getColumnConstraints().add(c);
			grid.add(Ui.label(heads[i], "muted"), i, 0);
		}
		int r = 1;
		for (var h : open) {
			var asset = assets.stream().filter(a -> a.id().equals(h.assetId())).findFirst().orElse(null);
			var name = new VBox(2, Ui.row(colorDot(Money.ASSET_COLOR.get(h.type())), Ui.label(h.name())), Ui.label(h.symbol(), "muted"));

			Node price;
			var source = h.priceSource() != null ? h.priceSource() : "MANUAL";
			if (!"MANUAL".equals(source)) {
				// Otomatik fiyat elle değiştirilmez; bir sonraki güncellemede üzerine yazılırdı
				price = right(Ui.label(Money.money(h.price(), h.priceCurrency())), sourceTag(h, asset));
			}
			else {
				var btn = Ui.button(Money.money(h.price(), h.priceCurrency()) + "  ✎", () -> editPrice(h), "ghost", "price");
				btn.setTooltip(new Tooltip("Güncel fiyatı gir"));
				price = h.priceIsFallback() ? right(btn, Ui.tag("son işlem fiyatı", "overdue"), sourceTag(h, asset)) : right(btn, sourceTag(h, asset));
			}

			Node[] cells = {
					name,
					right(Ui.label(Money.num(h.quantity()) + " " + (h.unit() != null ? h.unit() : ""))),
					right(Ui.label(Money.money(h.avgCost(), h.priceCurrency()))),
					price,
					right(Ui.label(Money.money(h.valueTry(), "TRY", 0)), Ui.label(Money.money(h.valueUsd(), "USD", 0), "muted")),
					right(Ui.label(Money.signed(h.unrealizedTry(), "TRY", 0) + " " + Money.pct(h.unrealizedPctTry()), Money.pnl(h.unrealizedTry())),
							Ui.label(Money.signed(h.unrealizedUsd(), "USD", 0) + " " + Money.pct(h.unrealizedPctUsd()), "small", Money.pnl(h.unrealizedUsd()))),
					right(Ui.label("%" + Money.num(h.weight()))) };
			for (int i = 0; i < cells.length; i++) {
				var cell = new VBox(cells[i]);
				cell.setAlignment(i == 0 ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
				cell.getStyleClass().add("table-cell");
				grid.add(cell, i, r);
			}
			r++;
		}
		return Ui.card(Ui.row(Ui.label("Varlıklar", "h2"), Ui.spacer(), Ui.button("+ Varlık", this::openAssetForm)), grid,
				Ui.label("Kâr/zarar her işlemin kendi günündeki kurla hesaplanır; dolar satırı \"dolar bazında\" kazancını gösterir.", "muted"));
	}

	private Node statusBar(PriceStatus status) {
		var text = new HBox(4);
		text.setAlignment(Pos.CENTER_LEFT);
		if (status == null) {
			text.getChildren().add(Ui.label("Fiyatlar henüz güncellenmedi", "muted"));
		}
		else {
			text.getChildren().add(Ui.label("Fiyatlar otomatik güncelleniyor · son: " + Money.timeAgo(status.finishedAt()) + " ·", "muted"));
			status.sources().forEach((k, v) -> {
				var l = Ui.label(k + ("ok".equals(v) ? " ✓" : " ✗"), "ok".equals(v) ? "pos" : "neg");
				if (!"ok".equals(v)) {
					l.setTooltip(new Tooltip(v));
				}
				text.getChildren().add(l);
			});
		}
		var refresh = Ui.button("↻ Güncelle", () -> { });
		refresh.setOnAction(e -> {
			refresh.setDisable(true);
			refresh.setText("Güncelleniyor…");
			Ui.async(api::refreshPrices, s -> refresh());
		});
		return Ui.row(text, Ui.spacer(), refresh);
	}

	private Node sourceTag(Holding h, Asset asset) {
		var source = h.priceSource() != null ? h.priceSource() : "MANUAL";
		var text = (h.priceError() != null ? "⚠ " : "") + Money.PRICE_SOURCE.getOrDefault(source, source)
				+ (!"MANUAL".equals(source) && h.priceUpdatedAt() != null ? " · " + Money.timeAgo(h.priceUpdatedAt()) : "");
		var variant = h.priceError() != null ? "sell" : !"MANUAL".equals(source) && Money.isStale(h.priceUpdatedAt()) ? "overdue" : null;
		var tag = Ui.tag(text, variant);
		tag.setTooltip(new Tooltip(h.priceError() != null ? "Son güncelleme başarısız: " + h.priceError() : "Fiyat kaynağını değiştirmek için tıkla"));
		tag.setOnMouseClicked(e -> editSource(asset));
		tag.getStyleClass().add("clickable");
		return tag;
	}

	private void editSource(Asset asset) {
		if (asset == null) {
			return;
		}
		var dialog = new Dialog<Map<String, Object>>();
		dialog.setTitle(asset.name() + " · fiyat kaynağı");
		dialog.setHeaderText(null);
		dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		var source = new ComboBox<String>();
		source.getItems().addAll("MANUAL", "TRUNCGIL", "YAHOO");
		if ("CURRENCY".equals(asset.type())) {
			source.getItems().add("RATE");
		}
		source.setConverter(TaskWidgets.converter(k -> k == null ? "" : switch (k) {
			case "TRUNCGIL" -> "Truncgil (altın)";
			case "YAHOO" -> "Yahoo (hisse)";
			case "RATE" -> "TCMB kuru";
			default -> "Elle";
		}));
		source.setValue(asset.priceSource() != null ? asset.priceSource() : "MANUAL");
		var symbol = field("gram-altin, ceyrek-altin, THYAO.IS, AAPL…", asset.sourceSymbol() != null ? asset.sourceSymbol() : "");
		symbol.disableProperty().bind(source.valueProperty().isNotEqualTo("TRUNCGIL").and(source.valueProperty().isNotEqualTo("YAHOO")));
		var form = new VBox(10, Ui.label("Kaynak", "muted"), source, Ui.label("Kaynaktaki sembol", "muted"), symbol);
		form.setPadding(new Insets(10));
		form.setPrefWidth(380);
		dialog.getDialogPane().setContent(form);
		dialog.setResultConverter(bt -> bt != ButtonType.OK ? null
				: body("priceSource", source.getValue(), "sourceSymbol", symbol.isDisabled() || symbol.getText().isBlank() ? null : symbol.getText().trim()));
		dialog.showAndWait().ifPresent(req -> Ui.run(() -> {
			api.setAssetSource(asset.id(), (String) req.get("priceSource"), (String) req.get("sourceSymbol"));
			api.refreshPrices();
		}, this::refresh));
	}

	private static VBox right(Node... nodes) {
		var box = new VBox(2, nodes);
		box.setAlignment(Pos.CENTER_RIGHT);
		return box;
	}

	private static Region colorDot(String color) {
		var dot = new Region();
		dot.setMinSize(10, 10);
		dot.setMaxSize(10, 10);
		dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
		return dot;
	}

	private VBox stat(String label, Node value, Node sub) {
		var card = Ui.card(Ui.label(label, "stat-label"), value, sub);
		card.setSpacing(4);
		HBox.setHgrow(card, Priority.ALWAYS);
		card.setMaxWidth(Double.MAX_VALUE);
		return card;
	}

	private Node rateRow(String currency, List<Rate> rates, BigDecimal fallback) {
		var rate = rates.stream().filter(r -> r.currency().equals(currency)).findFirst();
		Label value = rate.map(r -> {
			var l = Ui.label(Money.num(r.rateTry()), "h2");
			l.setTooltip(new Tooltip("TCMB döviz alış · " + Money.timeAgo(r.updatedAt())));
			return l;
		})
				.orElseGet(() -> fallback != null ? Ui.tag(Money.num(fallback) + " ?", "overdue") : Ui.tag("gir", "overdue"));
		var row = Ui.row(Ui.label(currency + "/TRY", "muted"), Ui.spacer(), value);
		row.getStyleClass().add("clickable");
		row.setOnMouseClicked(e -> askNumber(currency + "/TRY kuru", "1 " + currency + " kaç TL?", rate.map(Rate::rateTry).orElse(null))
				.ifPresent(v -> Ui.run(() -> api.setRate(currency, v), this::refresh)));
		return row;
	}

	private void editPrice(Holding h) {
		askNumber(h.name() + " güncel fiyatı", "Birim fiyat (" + Money.symbol(h.priceCurrency()) + ")", h.priceIsFallback() ? null : h.price())
				.ifPresent(v -> Ui.run(() -> api.setAssetPrice(h.assetId(), v), this::refresh));
	}

	/** Türkçe sayı girişli küçük pencere. */
	private Optional<BigDecimal> askNumber(String title, String prompt, BigDecimal current) {
		var dialog = new TextInputDialog(Money.input(current));
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(prompt);
		dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
		return dialog.showAndWait().flatMap(text -> {
			try {
				var v = Money.parse(text);
				if (v == null || v.signum() <= 0) {
					return Optional.empty();
				}
				return Optional.of(v);
			}
			catch (IllegalArgumentException e) {
				Ui.error(e);
				return Optional.empty();
			}
		});
	}

	private void openAssetForm() {
		var dialog = new Dialog<Map<String, Object>>();
		dialog.setTitle("Yeni varlık");
		dialog.setHeaderText(null);
		dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		var symbol = new TextField();
		symbol.setPromptText("Sembol (ör. THYAO)");
		var name = new TextField();
		name.setPromptText("Ad (ör. Türk Hava Yolları)");
		HBox.setHgrow(name, Priority.ALWAYS);
		var type = new ComboBox<String>();
		type.getItems().addAll("GOLD", "CURRENCY", "STOCK", "FUND");
		type.setConverter(TaskWidgets.converter(k -> k == null ? "" : Money.ASSET_ICON.get(k) + " " + Money.ASSET_TYPE.get(k)));
		var currency = new ComboBox<String>();
		currency.getItems().addAll("TRY", "USD");
		currency.setConverter(TaskWidgets.converter(c -> c == null ? "" : Money.symbol(c) + " " + c));
		var unit = new TextField();
		unit.setPromptText("Birim (gr, lot, adet)");

		var presets = new javafx.scene.layout.FlowPane(6, 6);
		for (var pr : PRESETS) {
			presets.getChildren().add(Ui.button(Money.ASSET_ICON.get(pr[2]) + " " + pr[5], () -> {
				symbol.setText(pr[0]);
				name.setText(pr[1]);
				type.setValue(pr[2]);
				currency.setValue(pr[3]);
				unit.setText(pr[4]);
				(pr[0].isEmpty() ? symbol : name).requestFocus();
			}));
		}
		var first = PRESETS.get(0);
		symbol.setText(first[0]);
		name.setText(first[1]);
		type.setValue(first[2]);
		currency.setValue(first[3]);
		unit.setText(first[4]);

		var form = new VBox(12, presets, Ui.row(symbol, name), Ui.row(type, Ui.label("Fiyat birimi", "muted"), currency, unit));
		form.setPadding(new Insets(10));
		form.setPrefWidth(560);
		dialog.getDialogPane().setContent(form);
		dialog.setResultConverter(bt -> bt != ButtonType.OK || symbol.getText().isBlank() || name.getText().isBlank() ? null
				: body("symbol", symbol.getText().trim(), "name", name.getText().trim(), "type", type.getValue(),
						"priceCurrency", currency.getValue(), "unit", unit.getText().isBlank() ? null : unit.getText().trim()));
		dialog.showAndWait().ifPresent(req -> Ui.run(() -> api.createAsset(req), this::refresh));
	}

	// =====================================================================
	// İşlemler
	// =====================================================================
	private void renderTrades(List<Asset> assets, List<Trade> trades, List<Rate> rates) {
		if (assets.isEmpty()) {
			body.getChildren().setAll(Ui.card(Ui.label("Önce bir varlık ekle", "h2"),
					Ui.label("\"Portföy\" sekmesinden ör. Gram altın, USD veya bir hisse ekleyebilirsin.", "muted")));
			return;
		}
		var usdRate = rates.stream().filter(r -> r.currency().equals("USD")).map(Rate::rateTry).findFirst().orElse(null);
		body.getChildren().setAll(tradeForm(assets, usdRate), tradeHistory(assets, trades));
	}

	private VBox tradeForm(List<Asset> assets, BigDecimal usdRate) {
		var e = editingTrade;
		var side = new ToggleGroup();
		var buy = new ToggleButton("Alış");
		var sell = new ToggleButton("Satış");
		buy.getStyleClass().addAll("segment", "buy");
		sell.getStyleClass().addAll("segment", "sell");
		buy.setToggleGroup(side);
		sell.setToggleGroup(side);
		(e != null && "SELL".equals(e.side()) ? sell : buy).setSelected(true);
		var sideBar = new HBox(buy, sell);
		sideBar.getStyleClass().add("toggle-group-bar");

		var asset = new ComboBox<Asset>();
		asset.getItems().addAll(assets);
		asset.setConverter(TaskWidgets.converter(a -> a == null ? "" : Money.ASSET_ICON.get(a.type()) + " " + a.name()));
		asset.setValue(e != null ? assets.stream().filter(a -> a.id().equals(e.assetId())).findFirst().orElse(assets.get(0)) : assets.get(0));
		var date = new DatePicker(e != null ? e.date() : LocalDate.now());
		var qty = field("Miktar", e != null ? Money.input(e.quantity()) : "");
		var price = field("Birim fiyat", e != null ? Money.input(e.unitPrice()) : "");
		var rate = field("Bilmiyorsan boş bırak", e != null && !e.rateEstimated() ? Money.input(e.usdTryRate()) : "");
		var fee = field("0", e != null ? Money.input(e.fee()) : "");
		var note = field("Not (isteğe bağlı)", e != null && e.note() != null ? e.note() : "");
		var preview = Ui.label("", "muted");
		var priceLabel = Ui.label("", "muted");
		var useRate = Ui.button(usdRate != null ? "güncel: " + Money.num(usdRate) : "", () -> rate.setText(Money.input(usdRate)), "link");
		useRate.setVisible(usdRate != null);

		Runnable update = () -> {
			var a = asset.getValue();
			boolean usdAsset = a != null && "CURRENCY".equals(a.type()) && "USD".equals(a.symbol());
			priceLabel.setText("Birim fiyat (" + Money.symbol(a != null ? a.priceCurrency() : "TRY") + ")");
			rate.setDisable(usdAsset);
			useRate.setDisable(usdAsset);
			if (usdAsset) {
				rate.setText(price.getText());
			}
			try {
				var q = Money.parse(qty.getText());
				var p = Money.parse(price.getText());
				var r = Optional.ofNullable(Money.parse(rate.getText())).orElse(usdRate);
				var f = Optional.ofNullable(Money.parse(fee.getText())).orElse(BigDecimal.ZERO);
				if (q == null || p == null || r == null || a == null) {
					preview.setText("");
					return;
				}
				var gross = q.multiply(p);
				var total = buy.isSelected() ? gross.add(f) : gross.subtract(f);
				var mc = java.math.MathContext.DECIMAL64;
				var tr = "USD".equals(a.priceCurrency()) ? total.multiply(r) : total;
				var usd = "USD".equals(a.priceCurrency()) ? total : total.divide(r, mc);
				preview.setText("Toplam: " + Money.money(tr, "TRY") + " · " + Money.money(usd, "USD"));
			}
			catch (IllegalArgumentException ex) {
				preview.setText("Geçersiz sayı");
			}
		};
		for (var f : List.of(qty, price, rate, fee)) {
			f.textProperty().addListener((o, a, b) -> update.run());
		}

		// Tarih seçilince o günün TCMB kuru otomatik dolar (kullanıcı kendisi yazdıysa dokunulmaz)
		String[] autoRate = { null };
		java.util.function.Consumer<LocalDate> fillRate = d -> {
			if (d == null) {
				return;
			}
			Ui.async(() -> Optional.ofNullable(api.historicalRate(d)), hr -> {
				hr.ifPresentOrElse(r -> {
					useRate.setText("TCMB " + r.rateDate().format(Ui.SHORT) + ": " + Money.num(r.rateTry()));
					useRate.setOnAction(ev -> rate.setText(Money.input(r.rateTry())));
					useRate.setVisible(true);
					var value = Money.input(r.rateTry());
					if (rate.getText().isBlank() || rate.getText().equals(autoRate[0])) {
						rate.setText(value);
					}
					autoRate[0] = value;
				}, () -> useRate.setVisible(false));
			});
		};
		date.valueProperty().addListener((o, a, b) -> fillRate.accept(b));
		if (e == null) {
			fillRate.accept(date.getValue());
		}
		asset.valueProperty().addListener((o, a, b) -> update.run());
		side.selectedToggleProperty().addListener((o, a, b) -> {
			if (b == null) {
				a.setSelected(true);
			}
			update.run();
		});
		update.run();

		var grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(4);
		for (int i = 0; i < 3; i++) {
			var c = new ColumnConstraints();
			c.setPercentWidth(33.3);
			grid.getColumnConstraints().add(c);
		}
		grid.addRow(0, Ui.label("Varlık", "muted"), Ui.label("Tarih", "muted"), Ui.label("Miktar", "muted"));
		grid.addRow(1, asset, date, qty);
		grid.addRow(2, priceLabel, Ui.row(Ui.label("O günkü USD/TRY", "muted"), useRate), Ui.label("Masraf (isteğe bağlı)", "muted"));
		grid.addRow(3, price, rate, fee);
		for (var n : List.of(asset, date)) {
			((Region) n).setMaxWidth(Double.MAX_VALUE);
		}

		var save = Ui.button(e != null ? "Güncelle" : "Kaydet", () -> {
			try {
				var a = asset.getValue();
				var req = body("assetId", a.id(), "side", buy.isSelected() ? "BUY" : "SELL", "date", date.getValue(),
						"quantity", Money.parse(qty.getText()), "unitPrice", Money.parse(price.getText()),
						"usdTryRate", Money.parse(rate.getText()), "fee", Money.parse(fee.getText()),
						"note", note.getText().isBlank() ? null : note.getText().trim());
				if (req.get("quantity") == null || req.get("unitPrice") == null) {
					Ui.error(new IllegalArgumentException("Miktar ve birim fiyat gerekli"));
					return;
				}
				Ui.run(() -> {
					if (e != null) {
						api.updateTrade(e.id(), req);
					}
					else {
						api.createTrade(req);
					}
				}, () -> {
					editingTrade = null;
					refresh();
				});
			}
			catch (IllegalArgumentException ex) {
				Ui.error(ex);
			}
		}, "primary");
		var actions = Ui.row(save);
		if (e != null) {
			actions.getChildren().add(Ui.button("Vazgeç", () -> { editingTrade = null; refresh(); }));
		}
		actions.getChildren().addAll(Ui.spacer(), preview);

		return Ui.card(Ui.row(Ui.label(e != null ? "İşlemi düzenle" : "Yeni işlem", "h2"), Ui.spacer(), sideBar), grid, note,
				Ui.label("Ondalık için virgül kullan (6.250,50); nokta binlik ayırıcıdır. Kur, seçtiğin tarihin TCMB kuruyla otomatik dolar.", "muted"),
				actions);
	}

	private static TextField field(String prompt, String value) {
		var f = new TextField(value);
		f.setPromptText(prompt);
		return f;
	}

	private VBox tradeHistory(List<Asset> assets, List<Trade> trades) {
		var filter = new ComboBox<Asset>();
		filter.getItems().add(null);
		filter.getItems().addAll(assets);
		filter.setConverter(TaskWidgets.converter(a -> a == null ? "Tüm varlıklar" : a.name()));
		filter.setValue(assets.stream().filter(a -> a.id().equals(tradeFilter)).findFirst().orElse(null));
		filter.setOnAction(ev -> {
			tradeFilter = filter.getValue() != null ? filter.getValue().id() : null;
			refresh();
		});

		var grid = new GridPane();
		grid.setHgap(14);
		String[] heads = { "Tarih", "İşlem", "Varlık", "Miktar", "Birim fiyat", "USD/TRY", "Toplam", "" };
		for (int i = 0; i < heads.length; i++) {
			var c = new ColumnConstraints();
			c.setHalignment(i < 3 ? HPos.LEFT : HPos.RIGHT);
			if (i == 2) {
				c.setHgrow(Priority.ALWAYS);
			}
			grid.getColumnConstraints().add(c);
			grid.add(Ui.label(heads[i], "muted"), i, 0);
		}
		if (trades.isEmpty()) {
			grid.add(Ui.empty("Henüz işlem yok"), 0, 1, heads.length, 1);
		}
		var dateFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Ui.TR);
		int r = 1;
		for (var t : trades) {
			var rateLabel = Ui.label((t.rateEstimated() ? "≈ " : "") + Money.num(t.usdTryRate()), t.rateEstimated() ? "warn" : "muted");
			if (t.rateEstimated()) {
				rateLabel.setTooltip(new Tooltip("Kur bilinmiyordu; kayıt anındaki güncel kur kullanıldı"));
			}
			var del = Ui.button("✕", () -> {
				if (Ui.confirm("Bu işlem silinsin mi?")) {
					Ui.run(() -> api.deleteTrade(t.id()), this::refresh);
				}
			}, "ghost", "del");
			var edit = Ui.button("✎", () -> {
				editingTrade = t;
				refresh();
			}, "ghost");
			Node[] cells = {
					Ui.label(t.date().format(dateFmt)),
					Ui.tag("BUY".equals(t.side()) ? "Alış" : "Satış", "BUY".equals(t.side()) ? "buy" : "sell"),
					t.note() != null ? new VBox(Ui.label(t.assetName()), Ui.label(t.note(), "muted")) : Ui.label(t.assetName()),
					Ui.label(Money.num(t.quantity()) + " " + (t.unit() != null ? t.unit() : "")),
					Ui.label(Money.money(t.unitPrice(), t.priceCurrency())),
					rateLabel,
					right(Ui.label(Money.money(t.totalTry(), "TRY")), Ui.label(Money.money(t.totalUsd(), "USD"), "muted")),
					Ui.row(edit, del) };
			for (int i = 0; i < cells.length; i++) {
				var cell = new VBox(cells[i]);
				cell.setAlignment(i < 3 ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
				cell.getStyleClass().add("table-cell");
				if (editingTrade != null && editingTrade.id().equals(t.id())) {
					cell.getStyleClass().add("selected-row");
				}
				grid.add(cell, i, r);
			}
			r++;
		}
		return Ui.card(Ui.row(Ui.label("İşlem geçmişi", "h2"), Ui.spacer(), filter), grid);
	}

	// =====================================================================
	// Ödemeler
	// =====================================================================
	private void renderPayments(PaymentsOverview o) {
		var active = o.payments().stream().filter(Payment::active).toList();
		var passive = o.payments().stream().filter(p -> !p.active()).toList();
		var missing = o.missingRates().isEmpty() ? Ui.label("", "muted")
				: Ui.label(String.join(", ", o.missingRates()) + " kuru girilmediği için bazı ödemeler hariç", "warn");
		var stats = new HBox(12,
				stat("Aylık sabit yük", Ui.label(Money.money(o.monthlyTotalTry(), "TRY", 0), "stat-value"), missing),
				stat("Bu ay ödenen", Ui.label(Money.money(o.paidThisMonthTry(), "TRY", 0), "stat-value", "plain"), new Label()),
				stat("Yaklaşan (7 gün)", Ui.label(String.valueOf(active.stream().filter(p -> p.daysUntilDue() <= 7).count()), "stat-value", "plain"), new Label()));

		var list = Ui.card(Ui.row(Ui.label("Düzenli ödemeler", "h2"), Ui.spacer(), Ui.button("+ Ödeme ekle", () -> openPaymentForm(null), "primary")));
		if (active.isEmpty()) {
			list.getChildren().add(Ui.empty("Henüz düzenli ödeme yok. Kira, fatura, abonelik ekleyebilirsin."));
		}
		active.forEach(p -> list.getChildren().add(paymentRow(api, p, this::refresh, false, () -> openPaymentForm(p))));
		if (!passive.isEmpty()) {
			list.getChildren().add(Ui.label("Duraklatılmış", "muted"));
			passive.forEach(p -> list.getChildren().add(paymentRow(api, p, this::refresh, false, () -> openPaymentForm(p))));
		}

		var recent = Ui.card(Ui.label("Son ödemeler", "h2"));
		if (o.recent().isEmpty()) {
			recent.getChildren().add(Ui.empty("Henüz ödeme kaydı yok"));
		}
		o.recent().forEach(l -> {
			var title = Ui.label(l.paymentName() + "  ·  " + l.dueDate().format(Ui.DAY_MONTH) + " vadesi");
			HBox.setHgrow(title, Priority.ALWAYS);
			title.setMaxWidth(Double.MAX_VALUE);
			var undo = Ui.button("↺", () -> {
				if (Ui.confirm("Bu ödeme kaydı geri alınsın mı?")) {
					Ui.run(() -> api.undoPayment(l.id()), this::refresh);
				}
			}, "ghost", "del");
			undo.setTooltip(new Tooltip("Geri al"));
			var row = Ui.row(title, Ui.label(l.paidDate().format(Ui.SHORT), "muted"), Ui.label(Money.money(l.amount(), l.currency()), "h2"), undo);
			row.getStyleClass().add("item");
			recent.getChildren().add(row);
		});
		body.getChildren().setAll(paymentsTabs(), stats, list, recent);
	}

	/** Ödeme satırı; Bugün ekranı ve mini mod da kullanır. */
	public static HBox paymentRow(ApiClient api, Payment p, Runnable reload, boolean compact, Runnable onEdit) {
		var title = new VBox(2);
		var name = Ui.row(Ui.label(p.name()));
		if (!compact && p.category() != null) {
			name.getChildren().add(Ui.tag(p.category(), null));
		}
		title.getChildren().add(name);
		if (!compact) {
			var info = Money.FREQUENCY.get(p.frequency()) + " · sonraki: " + p.nextDueDate().format(Ui.DAY_MONTH)
					+ (p.lastPaidDate() != null ? " · son ödeme " + p.lastPaidDate().format(Ui.SHORT) : "");
			title.getChildren().add(Ui.label(info, "muted"));
		}
		HBox.setHgrow(title, Priority.ALWAYS);

		var row = Ui.row(title);
		row.getStyleClass().add("item");
		if (p.active()) {
			long d = p.daysUntilDue();
			row.getChildren().add(d < 0 ? Ui.tag(-d + " gün gecikti", "overdue") : d == 0 ? Ui.tag("Bugün", "HIGH")
					: d == 1 ? Ui.tag("Yarın", "goal") : Ui.tag(d + " gün", null));
		}
		else {
			row.getStyleClass().add("done");
		}
		row.getChildren().add(Ui.label(Money.money(p.amount(), p.currency()), "h2"));
		if (p.active()) {
			var pay = Ui.button("✓ Ödendi", () -> Ui.run(() -> api.payPayment(p.id()), reload), "pay");
			pay.setTooltip(new Tooltip("Bu vadeyi ödendi olarak işaretle"));
			row.getChildren().add(pay);
		}
		if (onEdit != null) {
			row.getChildren().add(Ui.button("✎", onEdit, "ghost"));
			row.getChildren().add(Ui.button("✕", () -> {
				if (Ui.confirm("\"" + p.name() + "\" ve ödeme geçmişi silinsin mi?")) {
					Ui.run(() -> api.deletePayment(p.id()), reload);
				}
			}, "ghost", "del"));
		}
		return row;
	}

	private void openPaymentForm(Payment p) {
		var dialog = new Dialog<Map<String, Object>>();
		dialog.setTitle(p == null ? "Yeni düzenli ödeme" : "Ödemeyi düzenle");
		dialog.setHeaderText(null);
		dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		var name = field("Ad (ör. Kira, Netflix)", p != null ? p.name() : "");
		HBox.setHgrow(name, Priority.ALWAYS);
		var category = new ComboBox<String>();
		category.setEditable(true);
		category.getItems().addAll("Ev", "Fatura", "Abonelik", "Sigorta", "Kredi", "Eğitim", "Ulaşım");
		category.setPromptText("Kategori");
		category.setValue(p != null ? p.category() : null);
		var amount = field("Tutar", p != null ? Money.input(p.amount()) : "");
		var currency = new ComboBox<String>();
		currency.getItems().addAll("TRY", "USD", "EUR");
		currency.setConverter(TaskWidgets.converter(c -> c == null ? "" : Money.symbol(c) + " " + c));
		currency.setValue(p != null ? p.currency() : "TRY");
		var frequency = new ComboBox<String>();
		frequency.getItems().addAll(Money.FREQUENCY.keySet());
		frequency.setConverter(TaskWidgets.converter(f -> f == null ? "" : Money.FREQUENCY.get(f)));
		frequency.setValue(p != null ? p.frequency() : "MONTHLY");
		var next = new DatePicker(p != null ? p.nextDueDate() : LocalDate.now());
		var active = new CheckBox("Aktif");
		active.setSelected(p == null || p.active());
		var note = field("Not (isteğe bağlı)", p != null && p.note() != null ? p.note() : "");

		var row2 = Ui.row(amount, currency, frequency, Ui.label("Sonraki ödeme", "muted"), next);
		if (p != null) {
			row2.getChildren().add(active);
		}
		var form = new VBox(12, Ui.row(name, category), row2, note);
		form.setPadding(new Insets(10));
		form.setPrefWidth(600);
		dialog.getDialogPane().setContent(form);
		dialog.setOnShown(e -> name.requestFocus());
		dialog.setResultConverter(bt -> {
			if (bt != ButtonType.OK || name.getText().isBlank()) {
				return null;
			}
			try {
				var a = Money.parse(amount.getText());
				if (a == null || a.signum() <= 0) {
					throw new IllegalArgumentException("Tutar gerekli");
				}
				var cat = category.getEditor().getText();
				return body("name", name.getText().trim(), "category", cat == null || cat.isBlank() ? null : cat.trim(),
						"amount", a, "currency", currency.getValue(), "frequency", frequency.getValue(),
						"nextDueDate", next.getValue(), "active", active.isSelected(),
						"note", note.getText().isBlank() ? null : note.getText().trim());
			}
			catch (IllegalArgumentException ex) {
				Ui.error(ex);
				return null;
			}
		});
		dialog.showAndWait().ifPresent(req -> Ui.run(() -> {
			if (p == null) {
				api.createPayment(req);
			}
			else {
				api.updatePayment(p.id(), req);
			}
		}, this::refresh));
	}
}
