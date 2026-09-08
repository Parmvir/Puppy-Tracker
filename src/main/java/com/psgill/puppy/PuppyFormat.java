package com.psgill.puppy;

/**
 * Duration formatting for the overlay and panel.
 *
 * Its own class, like the sibling death-cost plugin's formatter, because
 * these are the strings most likely to be argued with — "is 90 seconds shown
 * as 1m or 1m 30s" is a question worth answering in a test rather than in
 * three slightly different places.
 */
final class PuppyFormat
{
	private PuppyFormat()
	{
	}

	/**
	 * Compact countdown: {@code 45s}, {@code 12m 30s}, {@code 1h 05m}.
	 *
	 * Seconds are dropped above an hour because at that range they are
	 * noise, and shown below a minute because at that range they are the
	 * whole message.
	 */
	static String duration(long millis)
	{
		long totalSeconds = Math.max(0, millis / 1000);
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;

		if (hours > 0)
		{
			return String.format("%dh %02dm", hours, minutes);
		}

		if (minutes > 0)
		{
			return String.format("%dm %02ds", minutes, seconds);
		}

		return seconds + "s";
	}

	/**
	 * A timer's remaining time as the overlay wants to read it: a plain
	 * countdown while there is time left, and how late you are once there is
	 * not. Null means no timer is running.
	 */
	static String remaining(Long remainingMillis)
	{
		if (remainingMillis == null)
		{
			return "--";
		}

		if (remainingMillis >= 0)
		{
			return duration(remainingMillis);
		}

		return "-" + duration(-remainingMillis);
	}

	/** Longer form for the panel, where there is room for words. */
	static String remainingLong(Long remainingMillis)
	{
		if (remainingMillis == null)
		{
			return "not tracked yet";
		}

		if (remainingMillis >= 0)
		{
			return "in " + duration(remainingMillis);
		}

		return duration(-remainingMillis) + " overdue";
	}

	/** Seconds as a profile author would write them: {@code 15m}, {@code 1h 30m}. */
	static String period(long seconds)
	{
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long rest = seconds % 60;

		if (hours > 0)
		{
			return minutes == 0 ? hours + "h" : String.format("%dh %02dm", hours, minutes);
		}

		if (minutes > 0)
		{
			return rest == 0 ? minutes + "m" : String.format("%dm %02ds", minutes, rest);
		}

		return seconds + "s";
	}
}
