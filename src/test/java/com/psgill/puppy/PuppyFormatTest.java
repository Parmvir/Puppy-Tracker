package com.psgill.puppy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PuppyFormatTest
{
	@Test
	public void showsSecondsUnderAMinute()
	{
		assertEquals("45s", PuppyFormat.duration(45_000));
		assertEquals("0s", PuppyFormat.duration(0));
	}

	@Test
	public void showsMinutesAndSecondsUnderAnHour()
	{
		assertEquals("12m 30s", PuppyFormat.duration(750_000));
		assertEquals("1m 00s", PuppyFormat.duration(60_000));
	}

	@Test
	public void dropsSecondsAboveAnHour()
	{
		assertEquals("1h 05m", PuppyFormat.duration(3_900_000));
	}

	@Test
	public void negativeDurationsClampRatherThanPrintingRubbish()
	{
		assertEquals("0s", PuppyFormat.duration(-5_000));
	}

	@Test
	public void remainingMarksOverdueWithASign()
	{
		assertEquals("2m 00s", PuppyFormat.remaining(120_000L));
		assertEquals("-2m 00s", PuppyFormat.remaining(-120_000L));
		assertEquals("--", PuppyFormat.remaining(null));
	}

	@Test
	public void remainingLongReadsAsWords()
	{
		assertEquals("in 2m 00s", PuppyFormat.remainingLong(120_000L));
		assertEquals("2m 00s overdue", PuppyFormat.remainingLong(-120_000L));
		assertEquals("not tracked yet", PuppyFormat.remainingLong(null));
	}

	@Test
	public void periodDropsEmptyTrailingUnits()
	{
		assertEquals("15m", PuppyFormat.period(900));
		assertEquals("1h", PuppyFormat.period(3600));
		assertEquals("1h 30m", PuppyFormat.period(5400));
		assertEquals("1m 30s", PuppyFormat.period(90));
	}
}
