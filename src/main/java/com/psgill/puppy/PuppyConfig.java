package com.psgill.puppy;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;

@ConfigGroup(PuppyConfig.GROUP)
public interface PuppyConfig extends Config
{
	String GROUP = "puppytracker";

	@ConfigItem(
		keyName = "hideWhenAway",
		name = "Hide when no puppy is following",
		description = "Hide the overlay unless a puppy is actually with you",
		position = 1
	)
	default boolean hideWhenAway()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideWhenGrown",
		name = "Hide once fully grown",
		description = "Hide the overlay when the puppy has finished growing into a dog",
		position = 2
	)
	default boolean hideWhenGrown()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyHungry",
		name = "Notify when hungry",
		description = "Notify 15 minutes after the last feed, when the game gives its hunger warning",
		position = 3
	)
	default Notification notifyHungry()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "notifyPaused",
		name = "Notify when growth stops",
		description = "Notify 20 minutes after the last feed, when the puppy stops growing",
		position = 4
	)
	default Notification notifyPaused()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "notifyGrown",
		name = "Notify when fully grown",
		description = "Notify when the puppy has finished its three hours of growing",
		position = 5
	)
	default Notification notifyGrown()
	{
		return Notification.ON;
	}
}
