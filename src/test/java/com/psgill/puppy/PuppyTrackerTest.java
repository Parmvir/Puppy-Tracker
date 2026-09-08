package com.psgill.puppy;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PuppyTrackerTest
{
	private static final long MINUTE = 60_000L;

	private PuppyTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new PuppyTracker();
		tracker.setFollowing(true, "Rover");
	}

	@Test
	public void theWikisNumbersAreTheOnesUsed()
	{
		assertEquals("three hours of active growth", 3 * 60 * 60, PuppyTracker.GROWTH_SECONDS);
		assertEquals("growth stops 20 minutes after a feed", 20 * 60, PuppyTracker.FEED_SECONDS);
		assertEquals("hunger warning at 15 minutes", 15 * 60, PuppyTracker.HUNGER_WARNING_SECONDS);
	}

	@Test
	public void growsWhileFedAndFollowing()
	{
		tracker.fed(0);
		tracker.tick(0);
		tracker.tick(10 * MINUTE);

		assertEquals(600, tracker.grownSeconds());
		assertEquals(170 * MINUTE, tracker.untilGrownMillis());
	}

	/**
	 * The rule the whole design turns on: time spent unfed does not count,
	 * so growth is seconds banked rather than a countdown from a start time.
	 */
	@Test
	public void doesNotGrowOnceTwentyMinutesUnfed()
	{
		tracker.fed(0);
		tracker.tick(0);
		tracker.tick(20 * MINUTE);

		long grownAtPause = tracker.grownSeconds();
		assertEquals(20 * 60, grownAtPause);

		// Two hours ignored, then fed again.
		tracker.tick(140 * MINUTE);
		assertEquals("paused time must not count", grownAtPause, tracker.grownSeconds());

		tracker.fed(140 * MINUTE);
		tracker.tick(150 * MINUTE);
		assertEquals(grownAtPause + 10 * 60, tracker.grownSeconds());
	}

	@Test
	public void doesNotGrowWhileNotFollowing()
	{
		tracker.fed(0);
		tracker.tick(0);
		tracker.setFollowing(false, null);
		tracker.tick(10 * MINUTE);

		assertEquals(0, tracker.grownSeconds());
	}

	@Test
	public void hungerAndPauseFallWhereTheWikiSaysTheyDo()
	{
		tracker.fed(0);

		assertFalse(tracker.isHungry(14 * MINUTE));
		assertTrue(tracker.isHungry(15 * MINUTE));
		assertFalse(tracker.isGrowthPaused(19 * MINUTE));
		assertTrue(tracker.isGrowthPaused(20 * MINUTE));

		assertEquals(5 * MINUTE, (long) tracker.untilHungryMillis(10 * MINUTE));
		assertEquals(10 * MINUTE, (long) tracker.untilPauseMillis(10 * MINUTE));
		assertNull("no countdown once it has already stopped", tracker.untilPauseMillis(25 * MINUTE));
	}

	@Test
	public void anUnfedPuppyReadsAsHungryRatherThanFreshlyFed()
	{
		assertTrue(tracker.isHungry(0));
		assertTrue(tracker.isGrowthPaused(0));
		assertNull(tracker.untilPauseMillis(0));
	}

	@Test
	public void feedingResumesGrowth()
	{
		tracker.fed(0);
		tracker.tick(0);
		tracker.tick(30 * MINUTE);
		assertTrue(tracker.isGrowthPaused(30 * MINUTE));

		tracker.fed(30 * MINUTE);
		assertFalse(tracker.isGrowthPaused(30 * MINUTE));

		tracker.tick(35 * MINUTE);
		assertEquals(25 * 60, tracker.grownSeconds());
	}

	@Test
	public void growingUpStopsTheClockAtThreeHours()
	{
		tracker.fed(0);
		tracker.tick(0);

		// Fed all the way through.
		for (long minute = 10; minute <= 190; minute += 10)
		{
			tracker.fed(minute * MINUTE);
			tracker.tick(minute * MINUTE);
		}

		assertTrue(tracker.isFullyGrown());
		assertEquals(PuppyTracker.GROWTH_SECONDS, tracker.grownSeconds());
		assertEquals(0, tracker.untilGrownMillis());
		assertEquals(1.0, tracker.growthFraction(), 0.001);
	}

	// ------------------------------------------------------------ guess age

	/** Verbatim from the game, 2026-09-08. */
	private static final String GUESS_AGE_REPLY =
		"After taking a good look at your puppy, you estimate their age is 37 minutes. "
			+ "They will grow into a dog in 2 hours 23 minutes, assuming you keep feeding them.";

	@Test
	public void readsBothHalvesOfTheRealGuessAgeReply()
	{
		assertTrue(PuppyTracker.isGuessAgeReply(GUESS_AGE_REPLY));
		assertEquals("the age clause alone", Long.valueOf(37 * 60),
			PuppyTracker.parseAgeSeconds(GUESS_AGE_REPLY));
		assertEquals("the remaining clause alone", Long.valueOf(2 * 3600 + 23 * 60),
			PuppyTracker.parseRemainingSeconds(GUESS_AGE_REPLY));
	}

	/**
	 * The bug this replaced: one regex over the whole line took "2 hour"
	 * from the second clause and "37 minutes" from the first, summing to
	 * 9420s and reporting 23 minutes left instead of 2h23m.
	 */
	@Test
	public void doesNotSpliceTheTwoDurationsTogether()
	{
		assertEquals(9420, 2 * 3600 + 37 * 60);

		tracker.syncRemaining(PuppyTracker.parseRemainingSeconds(GUESS_AGE_REPLY));

		assertEquals(2 * 3600 + 23 * 60, tracker.untilGrownMillis() / 1000);
		assertEquals(37 * 60, tracker.grownSeconds());
	}

	@Test
	public void theTwoHalvesOfTheReplyAgreeWithTheWikisThreeHours()
	{
		long age = PuppyTracker.parseAgeSeconds(GUESS_AGE_REPLY);
		long remaining = PuppyTracker.parseRemainingSeconds(GUESS_AGE_REPLY);

		assertEquals(PuppyTracker.GROWTH_SECONDS, age + remaining);
	}

	@Test
	public void anOrdinaryLineIsNotAGuessAgeReply()
	{
		assertFalse(PuppyTracker.isGuessAgeReply("Your puppy barks happily."));
		assertFalse(PuppyTracker.isGuessAgeReply(null));
		assertNull(PuppyTracker.parseRemainingSeconds("Your puppy barks happily."));
		assertNull(PuppyTracker.parseAgeSeconds(null));
	}

	@Test
	public void syncingFromRemainingOverwritesWhatWasInferred()
	{
		tracker.fed(0);
		tracker.tick(0);
		tracker.tick(10 * MINUTE);
		assertEquals(600, tracker.grownSeconds());

		tracker.syncRemaining(60 * 60);

		assertEquals(2 * 60 * 60, tracker.grownSeconds());
		assertEquals(60 * MINUTE, tracker.untilGrownMillis());
	}

	@Test
	public void noTimeLeftMeansItIsGrown()
	{
		tracker.syncRemaining(0);

		assertTrue(tracker.isFullyGrown());
		assertEquals(0, tracker.untilGrownMillis());
		assertEquals(PuppyTracker.GROWTH_SECONDS, tracker.grownSeconds());
	}

	@Test
	public void aGuessPastThreeHoursIsClampedRatherThanGoingNegative()
	{
		tracker.syncAge(4 * 60 * 60);
		assertTrue(tracker.isFullyGrown());
		assertEquals(PuppyTracker.GROWTH_SECONDS, tracker.grownSeconds());

		tracker.syncRemaining(5 * 60 * 60);
		assertEquals(0, tracker.grownSeconds());
	}

	@Test
	public void theStoppedGrowingMessageBacksTheFeedClockUp()
	{
		tracker.fed(0);
		assertFalse(tracker.isGrowthPaused(5 * MINUTE));

		tracker.growthStopped(5 * MINUTE);

		assertTrue(tracker.isGrowthPaused(5 * MINUTE));
	}

	// ---------------------------------------------------------- persistence

	@Test
	public void clocksSurviveARoundTripThroughStrings()
	{
		tracker.fed(3 * MINUTE);
		tracker.tick(3 * MINUTE);
		tracker.tick(13 * MINUTE);

		Map<String, String> state = tracker.save();

		PuppyTracker restored = new PuppyTracker();
		restored.load(state);

		assertEquals(tracker.grownSeconds(), restored.grownSeconds());
		assertEquals(tracker.lastFedMillis(), restored.lastFedMillis());
		assertEquals("Rover", restored.petName());
	}

	/**
	 * Logging out for the night and back in should report a puppy that has
	 * long since stopped growing, because that is what happened.
	 */
	@Test
	public void anOvernightLogoutComesBackStopped()
	{
		tracker.fed(0);
		tracker.tick(0);
		tracker.tick(10 * MINUTE);

		PuppyTracker restored = new PuppyTracker();
		restored.load(tracker.save());
		restored.setFollowing(true, "Rover");

		long nextMorning = 8 * 60 * MINUTE;
		assertTrue(restored.isGrowthPaused(nextMorning));

		restored.tick(nextMorning);
		restored.tick(nextMorning + 10 * MINUTE);
		assertEquals("no growing happened overnight", 600, restored.grownSeconds());
	}

	@Test
	public void garbageInTheSavedStateIsIgnoredRatherThanCrashing()
	{
		PuppyTracker restored = new PuppyTracker();
		restored.load(java.util.Collections.singletonMap("grownSeconds", "not a number"));

		assertEquals(0, restored.grownSeconds());
		assertFalse(restored.isFullyGrown());
	}

	@Test
	public void resetStartsAFreshPuppy()
	{
		tracker.syncAge(2 * 60 * 60);
		tracker.reset(5 * MINUTE);

		assertEquals(0, tracker.grownSeconds());
		assertFalse(tracker.isFullyGrown());
		assertFalse(tracker.isGrowthPaused(5 * MINUTE));
	}
}
