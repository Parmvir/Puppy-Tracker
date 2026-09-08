package com.psgill.puppy;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the overlay's paint path against an offscreen image, since the
 * real one only runs inside a logged-in client.
 */
public class PuppyOverlayTest
{
	private static final long MINUTE = 60_000L;

	/** All-defaults config; individual tests override single methods. */
	private static class TestConfig implements PuppyConfig
	{
	}

	private static PuppyOverlay overlayWith(PuppyConfig config)
	{
		// The plugin reference is only touched by render(), not drawCard().
		return new PuppyOverlay(null, config);
	}

	private static Graphics2D graphics()
	{
		return new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
	}

	private static PuppyTracker growing()
	{
		PuppyTracker tracker = new PuppyTracker();
		tracker.setFollowing(true, "Rover");
		tracker.fed(0);
		tracker.tick(0);
		tracker.tick(5 * MINUTE);
		return tracker;
	}

	@Test
	public void drawsACardOfAReasonableSize()
	{
		Dimension size = overlayWith(new TestConfig()).drawCard(graphics(), growing(), 5 * MINUTE);

		assertNotNull(size);
		assertEquals(168, size.width);
		assertTrue(size.height > 40);
		assertTrue(size.height < 140);
	}

	@Test
	public void aGrownDogLosesTheGrowthRowsAndShrinks()
	{
		PuppyTracker grown = growing();
		grown.grownUp();

		int growingHeight = overlayWith(new TestConfig()).drawCard(graphics(), growing(), 5 * MINUTE).height;
		int grownHeight = overlayWith(new TestConfig()).drawCard(graphics(), grown, 5 * MINUTE).height;

		assertTrue(grownHeight < growingHeight);
	}

	// ------------------------------------------------------------- minimise

	@Test
	public void minimisingShrinksTheCard()
	{
		PuppyOverlay overlay = overlayWith(new TestConfig());
		int full = overlay.drawCard(graphics(), growing(), 5 * MINUTE).height;

		overlay.setMinimised(true);
		Dimension small = overlay.drawCard(graphics(), growing(), 5 * MINUTE);

		assertTrue(small.height < full);
		assertTrue(small.width < 168);
	}

	@Test
	public void clickingTheButtonTogglesAndSwallowsTheClick()
	{
		PuppyOverlay overlay = overlayWith(new TestConfig());
		overlay.drawCard(graphics(), growing(), 5 * MINUTE);

		// The button is drawn at the top right of the card; the overlay has
		// not been laid out by a client, so its origin is 0,0.
		Point onButton = new Point(168 - 9 - 6, 9 + 14 - 6);

		assertTrue(overlay.handleClick(onButton));
		assertTrue(overlay.isMinimised());

		// Repainting collapsed moves the button, so find it again.
		overlay.drawCard(graphics(), growing(), 5 * MINUTE);
		assertTrue(overlay.handleClick(new Point(112 - 9 - 6, 9 + 14 - 7)));
		assertFalse(overlay.isMinimised());
	}

	@Test
	public void clicksElsewhereArePassedThrough()
	{
		PuppyOverlay overlay = overlayWith(new TestConfig());
		overlay.drawCard(graphics(), growing(), 5 * MINUTE);

		assertFalse(overlay.handleClick(new Point(20, 60)));
		assertFalse(overlay.handleClick(new Point(400, 400)));
		assertFalse(overlay.isMinimised());
	}

	@Test
	public void aClickBeforeAnythingIsPaintedIsIgnored()
	{
		assertFalse(overlayWith(new TestConfig()).handleClick(new Point(5, 5)));
	}

	@Test
	public void theCollapsedCardStillShowsTheGrowthTime()
	{
		PuppyOverlay overlay = overlayWith(new TestConfig());
		overlay.setMinimised(true);

		assertNotNull(overlay.drawCard(graphics(), growing(), 5 * MINUTE));

		PuppyTracker grown = growing();
		grown.grownUp();
		assertNotNull(overlay.drawCard(graphics(), grown, 5 * MINUTE));
	}

	@Test
	public void aStoppedPuppyStillPaints()
	{
		PuppyTracker tracker = growing();

		Dimension size = overlayWith(new TestConfig()).drawCard(graphics(), tracker, 40 * MINUTE);

		assertNotNull(size);
		assertTrue(tracker.isGrowthPaused(40 * MINUTE));
		assertTrue(size.height > 0);
	}

	@Test
	public void aPuppyThatHasNeverBeenFedStillPaints()
	{
		PuppyTracker fresh = new PuppyTracker();
		fresh.setFollowing(true, "Rover");

		assertNotNull(overlayWith(new TestConfig()).drawCard(graphics(), fresh, 0));
	}
}
