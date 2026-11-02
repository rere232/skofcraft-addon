# SKOFCRAFT: Random Addons

The things that can't go in [SKOFCRAFT](https://github.com/rere232/skofcraft) itself because they build on someone else's work. Requires SKOFCRAFT (and so Curios)
on Minecraft 1.20.1 with Forge 47.4 or newer.

## What's in it

**The Campfire Band.** A Whistle, Drums, a Banjo, a Flute, a Harmonica and a Travel Piano, each playing one part of the
same song. Right-click to play and again to stop. Anyone who starts later comes in on the current beat automatically.

They play the MIDI song in the jar, `data/skofcraft_addons/songs/band.mid`, which a data pack can replace. Parts go to
instruments by their General MIDI sound, and anything else goes to whichever instrument is free. If the MIDI song is
missing or can't be read they play the campfire song, `data/skofcraft_addons/songs/campfire.json`, which a data pack can
also replace.

Operators can run a bare `/playsound` to hear all of the campfire song once, and again to stop it. Vanilla's
`/playsound <sound> ...` still works as usual.

Operators can also run `/playwild` to have everyone online play the instruments' song together, once from the top, and
again to stop it. Nobody needs to hold anything: the parts are shared out among the players.

**The Sun.** Sneak-right-click the sun with an Orbital Designator, load a Giga ICBM on a launch pad, and launch.

## Building

This builds against a SKOFCRAFT jar in your local Maven repository, so publish that first, and again whenever you change
it:

```bash
# in the skofcraft repo
./gradlew publishToMavenLocal

# in this repo
./gradlew build
./gradlew runClient
```

## Credits

Unofficial fan project, not approved or endorsed by, or affiliated with, the makers of any game it takes inspiration from.
