package com.psgill.puppy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A puppy's two clocks: when it next wants feeding, and how much growing it
 * has actually done.
 *
 * The numbers are the wiki's, not mine. A puppy becomes a dog after about
 * three hours of *active* growth while following you; the hunger warning
 * comes fifteen minutes after a feed; and twenty minutes after a feed growth
 * stops until you feed it again. That last rule is the whole reason growth
 * is counted in seconds banked rather than as a countdown from a start time
 * — a puppy left unfed overnight is exactly as grown in the morning as it
 * was when you left, and a wall-clock countdown would cheerfully report it
 * fully grown.
 *
 * Puppies do not run away, so there is nothing here about losing one.
 *
 * Holds no RuneLite types, so the clock logic can be tested by handing it an
 * explicit {@code nowMillis} instead of an afternoon.
 */
class PuppyTracker
{
	/** Active growth needed to become a dog: three hours. */
	static final long GROWTH_SECONDS = 3 * 60 * 60;

	/** Growth stops this long after the last feed. */
	static final long FEED_SECONDS = 20 * 60;

	/** The game warns you this long after the last feed. */
	static final long HUNGER_WARNING_SECONDS = 15 * 60;

	/**
	 * Markers in the guess-age reply, which reads:
	 *
	 * "After taking a good look at your puppy, you estimate their age is 37
	 * minutes. They will grow into a dog in 2 hours 23 minutes, assuming you
	 * keep feeding them."
	 *
	 * Two durations in one line, which is what broke the first version of
	 * this: a regex for the first "hour" and the first "minute" anywhere in
	 * the string spliced the hours off the second number onto the minutes of
	 * the first. So each number is now read out of its own clause.
	 */
	private static final String AGE_MARKER = "age is";

	private static final String REMAINING_MARKER = "grow into a dog in";

	private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*hour");

	private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*minute");

	/** Growth banked so far, in seconds. */
	private long grownSeconds;

	/** When the puppy was last fed, or null if it has never been seen fed. */
	private Long lastFedMillis;

	/** Last time growth was accumulated, so ticks can add the gap. */
	private Long lastAccrualMillis;

	private boolean following;

	private boolean fullyGrown;

	private String petName;

	// ---------------------------------------------------------------- clocks

	/**
	 * Advances the growth clock to {@code nowMillis}.
	 *
	 * Growth only accrues while the puppy is following and has been fed
	 * inside the last twenty minutes, which is the wiki's rule stated
	 * directly. Accruing from the previous call rather than from a start
	 * time is what makes pausing free: stop calling it, or call it while
	 * hungry, and the total simply does not move.
	 */
	void tick(long nowMillis)
	{
		if (lastAccrualMillis == null)
		{
			lastAccrualMillis = nowMillis;
			return;
		}

		long from = lastAccrualMillis;
		lastAccrualMillis = nowMillis;

		if (fullyGrown || !following || lastFedMillis == null)
		{
			return;
		}

		// Credit only the part of the gap that fell inside the fed window,
		// rather than asking whether it is paused *now*. Ticks are not
		// evenly spaced — a login, a lag spike or a slow save can leave a
		// gap of minutes — and judging the whole gap by its final instant
		// throws away growth that genuinely happened before the food ran
		// out.
		long end = Math.min(nowMillis, lastFedMillis + FEED_SECONDS * 1000L);
		if (end <= from)
		{
			return;
		}

		grownSeconds = Math.min(GROWTH_SECONDS, grownSeconds + (end - from) / 1000);

		if (grownSeconds >= GROWTH_SECONDS)
		{
			fullyGrown = true;
		}
	}

	/** True once the puppy has gone twenty minutes without food. */
	boolean isGrowthPaused(long nowMillis)
	{
		return secondsSinceFed(nowMillis) >= FEED_SECONDS;
	}

	/** True once the game would have warned you, at fifteen minutes. */
	boolean isHungry(long nowMillis)
	{
		return secondsSinceFed(nowMillis) >= HUNGER_WARNING_SECONDS;
	}

	private long secondsSinceFed(long nowMillis)
	{
		// Never having seen a feed is treated as hungry rather than as
		// freshly fed: it is the reading that makes you go and check.
		return lastFedMillis == null ? Long.MAX_VALUE / 2 : (nowMillis - lastFedMillis) / 1000;
	}

	/** Milliseconds until growth pauses, or null when it already has. */
	Long untilPauseMillis(long nowMillis)
	{
		if (lastFedMillis == null)
		{
			return null;
		}

		long remaining = lastFedMillis + FEED_SECONDS * 1000L - nowMillis;
		return remaining <= 0 ? null : remaining;
	}

	/** Milliseconds until the hunger warning, or null once it is due. */
	Long untilHungryMillis(long nowMillis)
	{
		if (lastFedMillis == null)
		{
			return null;
		}

		long remaining = lastFedMillis + HUNGER_WARNING_SECONDS * 1000L - nowMillis;
		return remaining <= 0 ? null : remaining;
	}

	/** Remaining active growth in milliseconds; 0 once fully grown. */
	long untilGrownMillis()
	{
		return Math.max(0, GROWTH_SECONDS - grownSeconds) * 1000L;
	}

	/** 0 to 1 across the three hours. */
	double growthFraction()
	{
		return Math.min(1, (double) grownSeconds / GROWTH_SECONDS);
	}

	long grownSeconds()
	{
		return grownSeconds;
	}

	Long lastFedMillis()
	{
		return lastFedMillis;
	}

	boolean isFollowing()
	{
		return following;
	}

	boolean isFullyGrown()
	{
		return fullyGrown;
	}

	String petName()
	{
		return petName == null ? "Puppy" : petName;
	}

	// ---------------------------------------------------------------- events

	void setFollowing(boolean following, String petName)
	{
		this.following = following;

		if (petName != null && !petName.isEmpty())
		{
			this.petName = petName;
		}
	}

	/** Fed it. Resets the feed clock and lets growth run again. */
	void fed(long nowMillis)
	{
		lastFedMillis = nowMillis;
	}

	/**
	 * Syncs growth to the age the game just told us.
	 *
	 * This is the authoritative one. The other two clocks are inferred from
	 * what you did, and drift for every feed the plugin was not running to
	 * see; the guessed age is the game's own answer, so it replaces the
	 * banked total outright rather than being averaged into it.
	 */
	void syncAge(long ageSeconds)
	{
		grownSeconds = Math.max(0, Math.min(GROWTH_SECONDS, ageSeconds));
		fullyGrown = grownSeconds >= GROWTH_SECONDS;
	}

	/** True when this line looks like the reply to "Guess age". */
	static boolean isGuessAgeReply(String message)
	{
		if (message == null)
		{
			return false;
		}

		String text = message.toLowerCase(Locale.ROOT);
		return text.contains(AGE_MARKER) || text.contains(REMAINING_MARKER);
	}

	/**
	 * The "grow into a dog in ..." half of the reply, in seconds.
	 *
	 * This is the half worth having. It is what the overlay shows, and it is
	 * the game's own arithmetic on growth actually done — where the stated
	 * age is wall-clock and would count time the puppy spent paused.
	 */
	static Long parseRemainingSeconds(String message)
	{
		return clauseSeconds(message, REMAINING_MARKER, false);
	}

	/** The "their age is ..." half, kept as a fallback. */
	static Long parseAgeSeconds(String message)
	{
		return clauseSeconds(message, AGE_MARKER, true);
	}

	/**
	 * Reads the duration in the clause following {@code marker}, stopping at
	 * a full stop when {@code stopAtSentenceEnd} — which is what keeps the
	 * age clause from running on into the sentence about growing up.
	 */
	private static Long clauseSeconds(String message, String marker, boolean stopAtSentenceEnd)
	{
		if (message == null)
		{
			return null;
		}

		String text = message.toLowerCase(Locale.ROOT);
		int at = text.indexOf(marker);
		if (at < 0)
		{
			return null;
		}

		String clause = text.substring(at + marker.length());
		if (stopAtSentenceEnd)
		{
			int stop = clause.indexOf('.');
			if (stop >= 0)
			{
				clause = clause.substring(0, stop);
			}
		}

		long seconds = 0;
		boolean found = false;

		Matcher hours = HOURS.matcher(clause);
		if (hours.find())
		{
			seconds += Long.parseLong(hours.group(1)) * 3600;
			found = true;
		}

		Matcher minutes = MINUTES.matcher(clause);
		if (minutes.find())
		{
			seconds += Long.parseLong(minutes.group(1)) * 60;
			found = true;
		}

		return found ? seconds : null;
	}

	/** Syncs from the time the game says is left, rather than from age. */
	void syncRemaining(long remainingSeconds)
	{
		syncAge(GROWTH_SECONDS - remainingSeconds);
	}

	/** The game said growth has stopped, so make the feed clock agree. */
	void growthStopped(long nowMillis)
	{
		lastFedMillis = nowMillis - FEED_SECONDS * 1000L;
	}

	void grownUp()
	{
		grownSeconds = GROWTH_SECONDS;
		fullyGrown = true;
	}

	/** Starting over with a fresh puppy. */
	void reset(long nowMillis)
	{
		grownSeconds = 0;
		fullyGrown = false;
		lastFedMillis = nowMillis;
		lastAccrualMillis = nowMillis;
	}

	// ----------------------------------------------------------- persistence

	Map<String, String> save()
	{
		Map<String, String> state = new LinkedHashMap<>();
		state.put("grownSeconds", Long.toString(grownSeconds));
		state.put("fullyGrown", Boolean.toString(fullyGrown));

		if (lastFedMillis != null)
		{
			state.put("lastFed", Long.toString(lastFedMillis));
		}

		if (petName != null)
		{
			state.put("petName", petName);
		}

		return state;
	}

	void load(Map<String, String> state)
	{
		if (state == null)
		{
			return;
		}

		Long grown = parse(state.get("grownSeconds"));
		grownSeconds = grown == null ? 0 : Math.min(GROWTH_SECONDS, Math.max(0, grown));
		fullyGrown = Boolean.parseBoolean(state.get("fullyGrown")) || grownSeconds >= GROWTH_SECONDS;
		lastFedMillis = parse(state.get("lastFed"));

		String name = state.get("petName");
		if (name != null && !name.isEmpty())
		{
			petName = name;
		}
	}

	private static Long parse(String value)
	{
		if (value == null || value.isEmpty())
		{
			return null;
		}

		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
