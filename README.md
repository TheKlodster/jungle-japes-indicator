# Jungle Japes Indicator Plugin

Indicates when the banana peels have spawned and when a player steps on a banana peel
during the Ba-Ba fight in the Tombs of Amascut.

---

This plugin plays a sound effect to indicate that banana peels have spawned after
defeating the baboons during the boss fight with Ba-Ba.

Players that step on a banana peel during the fight are stunned, and a devastating and
embarrassing (but also relatable) sound effect is played to help be angry for you!

---

## Instructions
1. Download **Jungle Japes Indicator** plugin.
2. Turn it on.
3. Gaming.

Any users that have the plugin installed will be able to hear the sound effects together
in the room.

Credit to **Stuge** and **Rallittelija** for the sound effects.

Credit to the RuneLite team as well as the RuneLite Development Discord channel for their
help and guidance on making my first plugin.

Note: if you 1-tick the banana peel, it never spawns and Stuge's sound effect will not play.

---

## Changes

#### Version 1.0
- Initial release.

#### Version 1.1
- Fixed sound effect not playing on jar file. Prevented users from not being able to
hear the sound effects.
- Fixed the plugin assuming the player was still in a raid after exiting.

#### Version 1.2
- Implemented a 4-tick delay between sound effects for banana peels spawning &
banana slips.

#### Version 1.3
- Fixed an error that could occur in the plugin while loading into the game.
- Fixed the plugin staying active after logging out or hopping worlds.
- Fixed the plugin not activating when turned on inside a raid.
- Improved banana slip detection so the sound effect plays once per slip.
- Volume setting is now limited to 0-100, and a volume of 0 now mutes the sound effects.
- Sound effects now play through RuneLite's audio player.
- Updated to current RuneLite APIs.