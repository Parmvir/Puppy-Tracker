# Puppy Tracker — RuneLite Plugin

Feed and growth timers for a puppy, in the shape of the kitten tracker: a
small overlay of countdowns and nothing else.

Read-only. It watches the follower slot, your menu clicks and the chat box,
and draws an overlay. It never sends input or automates any in-game action.

## The numbers

All from the [Puppy wiki page](https://oldschool.runescape.wiki/w/Puppy), not
guessed:

| | |
| --- | --- |
| Grows into a dog after | **3 hours of active growth**, while following you |
| Hunger warning | **15 minutes** after the last feed |
| Growth stops | **20 minutes** after the last feed |
| Runs away if neglected | **No** — puppies stay puppies |

Because paused time does not count, growth is tracked as **seconds banked**,
not as a countdown from when you got it. Leave a puppy unfed overnight and it
is exactly as grown in the morning as when you left — which a wall-clock
countdown would get badly wrong.

## What it shows

A compact card, top-left by default (drag it with RuneLite's overlay editor):

- **A growth bar** across the three hours, red while growth is stopped
- **Grows in** — remaining active growth
- **Feed in** — time until growth stops, amber once the 15 minute hunger
  warning has passed, and `stopped growing` once it has run out
The accent stripe down the left edge is green while fed, amber once hungry,
red once growth has stopped.

The **minimise button** at the top right rolls the card down to a single line
carrying just the growth countdown, and back. Which state it is in is
remembered between sessions. Clicks on the button are swallowed, so
minimising does not also send your character walking there.

## How the timers stay in sync

Three things you already do keep it honest:

- **Feed it** — restarts the 20 minute clock and resumes growth. Read from
  the menu click, not the chat text, so the clock starts when you feed rather
  than when the message finishes printing. If the puppy refuses the food
  ("...would give them an upset stomach"), the feed is rolled back.
- **Guess age** (right-click → Interact → Guess age) — **the authoritative
  one.** Everything else is inferred from actions the plugin happened to be
  running for; this is the game's own answer, so it overwrites the banked
  total outright. Use it after any stretch played without the plugin.

Petting is deliberately not tracked. Puppies have no attention need — unlike
kittens they never run away — so a "last petted" readout was a number that
looked meaningful and was not.

The reply reads:

> After taking a good look at your puppy, you estimate their age is **37
> minutes**. They will grow into a dog in **2 hours 23 minutes**, assuming
> you keep feeding them.

Two durations in one line, and the plugin reads each from its own clause. A
single regex across the whole sentence takes the hours from the second number
and the minutes from the first — which is a real bug this had, reporting 23
minutes left instead of 2h23m. The **"grow into a dog in"** half is the one
used: it is the game's arithmetic on growth actually done, where the stated
age is wall-clock and would count paused time. A test holds that sentence
verbatim.

It also listens for *"Your puppy has stopped growing..."* and for the
grown-into-a-dog message, and believes both over its own arithmetic.

## Settings

Hide when no puppy is following, hide once fully grown, and separate
notifications for hungry (15m), growth stopped (20m) and fully
grown. Each notification is a full RuneLite one, so tray, sound and flash are
all per-event.

## State

Saved in RuneLite's config under `puppytracker.state.*`: seconds of growth
banked, and the timestamp of the last feed. Absolute timestamps, so a logout
is correctly reported as time the puppy spent not growing.

## Building

```bash
./gradlew --offline build
```

Java 11, RuneLite pinned at 1.12.35. `./gradlew runPlugin` launches a full
client with the plugin loaded, using your existing `~/.runelite` profile.

## Tests

31 tests, no client needed. `PuppyTracker` holds no RuneLite types and takes
an explicit `nowMillis`, so three hours of puppy-raising is a few lines of a
test. The overlay is tested by painting to an offscreen image.

```bash
./gradlew --offline test
```

## Author

**PsGill-** — [github.com/Parmvir](https://github.com/Parmvir)

Issues and pull requests welcome at
[Parmvir/Puppy-Tracker](https://github.com/Parmvir/Puppy-Tracker).
