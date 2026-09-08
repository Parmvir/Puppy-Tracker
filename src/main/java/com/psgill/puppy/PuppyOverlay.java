package com.psgill.puppy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Two lines and a bar: how long until it needs feeding, and how long until
 * it is a dog.
 *
 * Painted by hand rather than stacked out of RuneLite's Title/Line
 * components, matching the sibling death-cost plugin — the growth bar and
 * the colour change on the feed row are the two things worth having, and
 * neither is something the stock rows do.
 */
class PuppyOverlay extends Overlay
{
	private static final int WIDTH = 168;
	private static final int MINIMISED_WIDTH = 112;
	private static final int BUTTON = 13;
	private static final int PAD = 9;
	private static final int TITLE_H = 14;
	private static final int ROW_H = 16;
	private static final int BAR_H = 5;
	private static final int BAR_GAP = 6;
	private static final int CORNER = 10;
	private static final int STRIPE_W = 3;

	// Alpha kept low enough to read the game through the card. The text and
	// the accent stripe stay fully opaque — it is the panel behind them that
	// gives way, so legibility does not pay for the transparency.
	private static final Color BACKGROUND = new Color(24, 24, 24, 140);
	private static final Color LABEL = new Color(176, 174, 163);
	private static final Color VALUE = new Color(238, 238, 238);
	private static final Color BAR_TRACK = new Color(255, 255, 255, 45);

	private static final Color OK = new Color(0x8B, 0xC3, 0x4A);
	private static final Color WARN = new Color(0xFF, 0x98, 0x1F);
	private static final Color STOPPED = new Color(0xE2, 0x4B, 0x4A);

	private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
	private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private static final Font VALUE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

	private final PuppyPlugin plugin;
	private final PuppyConfig config;

	/**
	 * Where the minimise button was last drawn, relative to the card.
	 *
	 * Kept from the paint rather than computed again at click time, because
	 * the card changes height with what it is showing and two pieces of
	 * geometry that have to agree are one more thing to get out of step.
	 */
	private final Rectangle buttonBounds = new Rectangle();

	private boolean minimised;

	@Inject
	PuppyOverlay(PuppyPlugin plugin, PuppyConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	boolean isMinimised()
	{
		return minimised;
	}

	void setMinimised(boolean minimised)
	{
		this.minimised = minimised;
	}

	/**
	 * Handles a click at a canvas point, returning true when it was ours.
	 *
	 * {@link #getBounds} is where RuneLite last laid the overlay out, so the
	 * button's screen position is that plus where it was painted inside the
	 * card. Returning true tells the plugin to swallow the click, so
	 * minimising the overlay does not also walk your character there.
	 */
	boolean handleClick(Point canvasPoint)
	{
		// The plugin check is skipped when there is no plugin, which is how
		// the paint tests drive this.
		if (buttonBounds.isEmpty() || (plugin != null && !plugin.shouldRender()))
		{
			return false;
		}

		Rectangle overlayBounds = getBounds();
		Rectangle button = new Rectangle(
			overlayBounds.x + buttonBounds.x,
			overlayBounds.y + buttonBounds.y,
			buttonBounds.width,
			buttonBounds.height);

		if (!button.contains(canvasPoint))
		{
			return false;
		}

		minimised = !minimised;
		return true;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.shouldRender())
		{
			return null;
		}

		return drawCard(g, plugin.tracker(), System.currentTimeMillis());
	}

	/**
	 * Paints the card and returns its size. Split out from {@link #render},
	 * which only runs inside a live client, so the paint path can be
	 * exercised against an offscreen image in a test.
	 */
	Dimension drawCard(Graphics2D g, PuppyTracker tracker, long now)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		boolean paused = tracker.isGrowthPaused(now);
		boolean grown = tracker.isFullyGrown();

		if (minimised)
		{
			return drawMinimised(g, tracker, now, grown, paused);
		}

		int rows = grown ? 0 : 2;
		int height = PAD + TITLE_H + (grown ? 0 : BAR_GAP + BAR_H) + rows * ROW_H + PAD;

		Color accent = grown ? OK : paused ? STOPPED : tracker.isHungry(now) ? WARN : OK;

		g.setColor(BACKGROUND);
		g.fill(new RoundRectangle2D.Float(0, 0, WIDTH, height, CORNER, CORNER));
		g.setColor(accent);
		g.fill(new RoundRectangle2D.Float(0, 0, STRIPE_W + CORNER, height, CORNER, CORNER));
		g.setColor(BACKGROUND);
		g.fillRect(STRIPE_W, 0, CORNER, height);

		int left = PAD;
		int right = WIDTH - PAD;
		int y = PAD + TITLE_H - 3;

		g.setFont(TITLE_FONT);
		g.setColor(VALUE);
		g.drawString(grown ? tracker.petName() + " (grown)" : tracker.petName(), left, y);

		drawButton(g, right, y, "-");

		if (!grown)
		{
			y += BAR_GAP;
			g.setColor(BAR_TRACK);
			g.fillRect(left, y, right - left, BAR_H);
			g.setColor(paused ? STOPPED : OK);
			g.fillRect(left, y, (int) ((right - left) * tracker.growthFraction()), BAR_H);
			y += BAR_H;

			y += ROW_H;
			row(g, "Grows in", PuppyFormat.duration(tracker.untilGrownMillis()), VALUE, left, right, y);

			y += ROW_H;
			Long untilPause = tracker.untilPauseMillis(now);
			if (paused)
			{
				row(g, "Feed", "stopped growing", STOPPED, left, right, y);
			}
			else
			{
				Long untilHungry = tracker.untilHungryMillis(now);
				row(g, "Feed in", PuppyFormat.duration(untilPause),
					untilHungry == null ? WARN : VALUE, left, right, y);
			}
		}

		return new Dimension(WIDTH, height);
	}

	/**
	 * The collapsed card: one row carrying the only number worth glancing
	 * at, plus the button to bring the rest back.
	 */
	private Dimension drawMinimised(Graphics2D g, PuppyTracker tracker, long now,
		boolean grown, boolean paused)
	{
		int height = PAD + TITLE_H + PAD - 4;
		int left = PAD;
		int right = MINIMISED_WIDTH - PAD;
		int y = PAD + TITLE_H - 5;

		Color accent = grown ? OK : paused ? STOPPED : tracker.isHungry(now) ? WARN : OK;

		g.setColor(BACKGROUND);
		g.fill(new RoundRectangle2D.Float(0, 0, MINIMISED_WIDTH, height, CORNER, CORNER));
		g.setColor(accent);
		g.fill(new RoundRectangle2D.Float(0, 0, STRIPE_W + CORNER, height, CORNER, CORNER));
		g.setColor(BACKGROUND);
		g.fillRect(STRIPE_W, 0, CORNER, height);

		g.setFont(VALUE_FONT);
		g.setColor(grown ? OK : paused ? STOPPED : VALUE);
		g.drawString(grown ? "Grown" : PuppyFormat.duration(tracker.untilGrownMillis()), left, y);

		drawButton(g, right, y, "+");

		return new Dimension(MINIMISED_WIDTH, height);
	}

	/**
	 * Draws the minimise/restore square and remembers where it went.
	 */
	private void drawButton(Graphics2D g, int right, int baseline, String glyph)
	{
		int x = right - BUTTON;
		int yTop = baseline - BUTTON + 2;

		g.setColor(new Color(255, 255, 255, 30));
		g.fill(new RoundRectangle2D.Float(x, yTop, BUTTON, BUTTON, 4, 4));
		g.setFont(VALUE_FONT);
		g.setColor(LABEL);

		int glyphWidth = g.getFontMetrics().stringWidth(glyph);
		g.drawString(glyph, x + (BUTTON - glyphWidth) / 2, yTop + BUTTON - 3);

		buttonBounds.setBounds(x, yTop, BUTTON, BUTTON);
	}

	private static void row(Graphics2D g, String label, String value, Color valueColour,
		int left, int right, int baseline)
	{
		g.setFont(LABEL_FONT);
		g.setColor(LABEL);
		g.drawString(label, left, baseline);

		g.setFont(VALUE_FONT);
		g.setColor(valueColour);
		g.drawString(value, right - g.getFontMetrics().stringWidth(value), baseline);
	}
}
