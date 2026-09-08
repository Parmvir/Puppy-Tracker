package com.psgill.puppy;

import com.google.inject.Provides;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Tracks a puppy growing into a dog, in the shape of the kitten tracker:
 * an overlay of countdowns, and nothing else.
 *
 * The three interactions the player already performs are what keep it
 * honest. Feeding restarts the twenty minute clock that growth depends on;
 * petting is recorded because it was asked for; and guessing the puppy's age
 * asks the game how grown it actually is, which overwrites whatever this had
 * inferred. That last one is the important one — everything else is deduced
 * from actions the plugin happened to be running for, and the guess-age
 * reply is the only number that comes from the game itself.
 */
@Slf4j
@PluginDescriptor(
	name = "Puppy Tracker",
	description = "Feed and growth timers for a puppy, in the style of the kitten tracker",
	tags = {"puppy", "dog", "pet", "kitten", "growth", "timer", "overlay"}
)
public class PuppyPlugin extends Plugin
{
	/** Config keys for the saved clocks all start with this. */
	private static final String STATE_PREFIX = "state.";

	/** Whether the overlay is rolled up. Remembered between sessions. */
	private static final String MINIMISED_KEY = "minimised";

	/** How often the clocks are written out, in ticks. */
	private static final int SAVE_INTERVAL_TICKS = 50;

	/** Milliseconds after feeding within which a refusal undoes it. */
	private static final long FEED_REFUSAL_WINDOW_MILLIS = 3000;

	/** The game's own words when a puppy has gone twenty minutes unfed. */
	private static final String STOPPED_GROWING = "has stopped growing";

	private static final String[] GROWN_UP = {
		"has grown into",
		"is now an adult",
		"fully grown"
	};

	/** Fragments meaning the puppy turned the food down, so it was not fed. */
	private static final String[] REFUSED = {
		"upset stomach",
		"would not be good",
		"doesn't want",
		"does not want",
		"turns its nose"
	};

	@Inject
	private Client client;

	@Inject
	private PuppyConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private PuppyOverlay overlay;

	@Inject
	private MouseManager mouseManager;

	/**
	 * Clicks on the minimise button.
	 *
	 * Overlays get no click handling of their own, so this watches every
	 * press and asks the overlay whether it landed on the button. A press
	 * that did is consumed, or minimising the overlay would also send your
	 * character walking to that spot.
	 */
	private final MouseAdapter mouseAdapter = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (overlay.handleClick(event.getPoint()))
			{
				configManager.setConfiguration(PuppyConfig.GROUP, MINIMISED_KEY,
					Boolean.toString(overlay.isMinimised()));
				event.consume();
			}

			return event;
		}
	};

	private final PuppyTracker tracker = new PuppyTracker();

	private int ticks;

	/** When a feed was recorded, so a refusal can undo it. Null if none. */
	private Long fedAtMillis;

	/** What the feed clock read before the feed being second-guessed. */
	private Long fedPreviousMillis;

	private boolean warnedHungry;
	private boolean warnedPaused;
	private boolean warnedGrown;

	@Provides
	PuppyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PuppyConfig.class);
	}

	@Override
	protected void startUp()
	{
		tracker.load(readState());
		overlay.setMinimised(Boolean.parseBoolean(
			configManager.getConfiguration(PuppyConfig.GROUP, MINIMISED_KEY)));

		overlayManager.add(overlay);
		mouseManager.registerMouseListener(mouseAdapter);
	}

	@Override
	protected void shutDown()
	{
		saveState();
		mouseManager.unregisterMouseListener(mouseAdapter);
		overlayManager.remove(overlay);
	}

	PuppyTracker tracker()
	{
		return tracker;
	}

	// ---------------------------------------------------------------- events

	@Subscribe
	public void onGameTick(GameTick event)
	{
		ticks++;

		long now = System.currentTimeMillis();
		NPC follower = client.getFollower();
		tracker.setFollowing(follower != null, follower == null ? null : follower.getName());
		tracker.tick(now);

		checkNotifications(now);

		if (ticks % SAVE_INTERVAL_TICKS == 0)
		{
			saveState();
		}
	}

	/**
	 * Watches what you click rather than what the game says.
	 *
	 * Feeding and petting are the two things whose chat wording I could not
	 * confirm, and a menu click is not wording — it is the action itself. It
	 * is also earlier: the clock starts when you feed, not when the sentence
	 * about feeding finishes printing.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
		String target = Text.removeTags(event.getMenuTarget());
		long now = System.currentTimeMillis();

		// Kept at debug: matching the wrong menu string is indistinguishable
		// from the plugin doing nothing, so when feeding stops registering
		// this is the first thing worth turning on. Feeding arrives as
		// option 'use' with target 'Cooked karambwan -> Shiba puppy'; the
		// earlier click that only selects the item has no pet in its target
		// and is correctly ignored.
		NPC follower = client.getFollower();
		if (follower != null && !target.isEmpty())
		{
			log.debug("puppy menu: option='{}' target='{}' follower='{}'",
				option, target, follower.getName());
		}

		if (!isPuppyTarget(event.getMenuTarget()))
		{
			return;
		}

		if (option.contains("feed") || option.equals("use"))
		{
			// Recorded now and undone if the puppy refuses, because a feed
			// that never happened would silently shift every later number.
			fedPreviousMillis = tracker.lastFedMillis();
			fedAtMillis = now;
			tracker.fed(now);
			resetWarnings();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.MESBOX
			&& event.getType() != ChatMessageType.DIALOG)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		String lower = message.toLowerCase(Locale.ROOT);
		long now = System.currentTimeMillis();

		if (containsAny(lower, REFUSED) && fedAtMillis != null
			&& now - fedAtMillis <= FEED_REFUSAL_WINDOW_MILLIS)
		{
			tracker.load(withFed(fedPreviousMillis));
			fedAtMillis = null;
			log.debug("puppy refused the food, feed clock rolled back");
			return;
		}

		if (lower.contains(STOPPED_GROWING))
		{
			tracker.growthStopped(now);
			return;
		}

		if (containsAny(lower, GROWN_UP) && lower.contains("dog"))
		{
			tracker.grownUp();
			return;
		}

		// The answer to "Guess age", which names itself: no need to have
		// spotted the click that asked for it. The first version keyed this
		// off a menu match and a tick window, and both were wrong — "Guess
		// age" is a dialog choice rather than an NPC menu option, so the
		// click was never seen.
		if (PuppyTracker.isGuessAgeReply(message))
		{
			Long remaining = PuppyTracker.parseRemainingSeconds(message);
			if (remaining != null)
			{
				tracker.syncRemaining(remaining);
				log.debug("synced from '{}': {}s left", message, remaining);
				return;
			}

			Long age = PuppyTracker.parseAgeSeconds(message);
			if (age != null)
			{
				tracker.syncAge(age);
				log.debug("synced from '{}': age {}s", message, age);
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			tracker.setFollowing(false, null);
			saveState();
		}
	}

	/**
	 * Rolls the feed clock back to a known value.
	 *
	 * Goes through the save format rather than adding a setter, so there is
	 * one way in and out of that field and no second path to keep correct.
	 */
	private Map<String, String> withFed(Long fedMillis)
	{
		Map<String, String> state = tracker.save();
		if (fedMillis == null)
		{
			state.remove("lastFed");
		}
		else
		{
			state.put("lastFed", Long.toString(fedMillis));
		}

		return state;
	}

	private boolean isPuppyTarget(String target)
	{
		if (target == null || target.isEmpty())
		{
			return false;
		}

		NPC follower = client.getFollower();
		if (follower == null || follower.getName() == null)
		{
			return false;
		}

		// Whatever is following you is the pet being tracked, whatever it is
		// called. Matching on the word "puppy" was the first version of this
		// and it meant a renamed pet read as no pet at all.
		return Text.removeTags(target).toLowerCase(Locale.ROOT)
			.contains(follower.getName().toLowerCase(Locale.ROOT));
	}

	private void checkNotifications(long now)
	{
		if (!tracker.isFollowing())
		{
			return;
		}

		if (tracker.isFullyGrown())
		{
			if (!warnedGrown)
			{
				warnedGrown = true;
				notifier.notify(config.notifyGrown(), "Your puppy has grown into a dog");
			}

			return;
		}

		if (tracker.isGrowthPaused(now) && !warnedPaused)
		{
			warnedPaused = true;
			notifier.notify(config.notifyPaused(), "Your puppy has stopped growing - feed it");
		}
		else if (tracker.isHungry(now) && !warnedHungry)
		{
			warnedHungry = true;
			notifier.notify(config.notifyHungry(), "Your puppy is hungry");
		}
	}

	private void resetWarnings()
	{
		warnedHungry = false;
		warnedPaused = false;
	}

	private static boolean containsAny(String haystack, String[] fragments)
	{
		for (String fragment : fragments)
		{
			if (haystack.contains(fragment))
			{
				return true;
			}
		}

		return false;
	}

	boolean shouldRender()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		if (config.hideWhenAway() && !tracker.isFollowing())
		{
			return false;
		}

		return !(config.hideWhenGrown() && tracker.isFullyGrown());
	}

	// ----------------------------------------------------------- persistence

	private void saveState()
	{
		for (Map.Entry<String, String> entry : tracker.save().entrySet())
		{
			configManager.setConfiguration(PuppyConfig.GROUP, STATE_PREFIX + entry.getKey(), entry.getValue());
		}
	}

	private Map<String, String> readState()
	{
		Map<String, String> state = new LinkedHashMap<>();
		String prefix = PuppyConfig.GROUP + "." + STATE_PREFIX;
		List<String> keys = configManager.getConfigurationKeys(prefix);

		for (String key : keys)
		{
			int cut = key.indexOf(prefix);
			if (cut < 0)
			{
				continue;
			}

			String shortKey = key.substring(cut + prefix.length());
			String value = configManager.getConfiguration(PuppyConfig.GROUP, STATE_PREFIX + shortKey);
			if (value != null)
			{
				state.put(shortKey, value);
			}
		}

		return state;
	}
}
