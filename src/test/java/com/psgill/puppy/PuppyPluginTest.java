package com.psgill.puppy;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a full RuneLite client with this plugin already loaded and
 * enabled — RuneLite's own standard way of running a plugin under
 * development, no sideloading tool needed. Uses your existing profile and
 * login from ~/.runelite, same as the normal client.
 *
 * Run it with {@code ./gradlew runPlugin}, or right-click > Run in IntelliJ.
 *
 * Holds no {@code @Test} methods, so Gradle's test task ignores it.
 */
public class PuppyPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PuppyPlugin.class);
		RuneLite.main(args);
	}
}
