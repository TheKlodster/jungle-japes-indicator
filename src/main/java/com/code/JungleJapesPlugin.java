package com.code;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@PluginDescriptor(
	name = "Jungle Japes Indicator"
)
public class JungleJapesPlugin extends Plugin
{
	/**
	 * ID of the banana peel game object that spawns after a baboon dies.
	 */
	private static final int BANANA_GAME_OBJECT_ID = 45755;

	/**
	 * Animation ID played when a player slips on a banana peel.
	 */
	private static final int BANANA_SLIP_ANIMATION_ID = 4030;

	/**
	 * Spot animation (graphic) ID shown on a player who slips on a banana peel.
	 */
	private static final int BANANA_GRAPHICS_ID = 1575;

	/**
	 * Map region ID of the Tombs of Amascut lobby.
	 */
	private static final int TOA_LOBBY_ID = 13454;

	/**
	 * Map region ID of the Nexus.
	 */
	private static final int TOA_NEXUS_ID = 14160;

	/**
	 * Map region ID of the Apmeken room.
	 */
	private static final int TOA_APMEKEN_ID = 15186;

	/**
	 * Map region ID of the Ba-Ba room.
	 */
	private static final int TOA_BABA_ID = 15188;

	/**
	 * Game ticks to wait between sound effects.
	 */
	private static final int BANANA_DELAY_TICKS = 4;

	/**
	 * Returned by {@link #getRegion()} when the local player is not available.
	 */
	private static final int UNKNOWN_REGION = -1;

	/**
	 * The RuneLite client.
	 */
	@Inject
	private Client client;

	/**
	 * Used to run code on the client thread.
	 */
	@Inject
	private ClientThread clientThread;

	/**
	 * The plugin's config, used for the volume.
	 */
	@Inject
	private JungleJapesConfig config;

	/**
	 * Whether the player is in a raid with an invocation level above 0, which enables the sound effects.
	 */
	@Getter
	@VisibleForTesting
	private boolean inToaRaid;

	/**
	 * The sound effect currently loaded, or null if none.
	 */
	private Clip clip;

	/**
	 * Game ticks remaining before another sound effect can play.
	 */
	private int bananaDelay = 0;

	/**
	 * The raid's invocation level, remembered once read in the Nexus, Apmeken or Ba-Ba rooms.
	 */
	private int invocationLevel = 0;

	/**
	 * Resets the state, and checks the raid state in case the plugin was turned on inside a raid.
	 */
	@Override
	protected void startUp()
	{
		resetState();
		bananaDelay = 0;

		// if the plugin was turned on inside the raid, check if there is an invocation level.
		clientThread.invokeLater(this::updateRaidState);
	}

	/**
	 * Resets the state and releases the audio clip.
	 */
	@Override
	protected void shutDown()
	{
		resetState();
		bananaDelay = 0;
		closeClip();
	}

	/**
	 * Counts down the delay between sound effects.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (bananaDelay > 0)
		{
			bananaDelay--;
		}
	}

	/**
	 * Resets the raid state when the player logs out or hops worlds.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			resetState();
		}
	}

	/**
	 * Plays the slip sound when a player slips on a banana peel.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged animationChanged)
	{
		Actor actor = animationChanged.getActor();
		if (!(actor instanceof Player))
		{
			return;
		}

		if (bananaDelay > 0)
		{
			return;
		}

		if (!inToaRaid)
		{
			return;
		}

		if (actor.getAnimation() == BANANA_SLIP_ANIMATION_ID || actor.hasSpotAnim(BANANA_GRAPHICS_ID))
		{
			playSound("rallittelija");
			bananaDelay = BANANA_DELAY_TICKS;
		}
	}

	/**
	 * Plays the spawn sound when a banana peel spawns.
	 */
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned)
	{
		if (bananaDelay > 0)
		{
			return;
		}

		if (!inToaRaid)
		{
			return;
		}

		if (gameObjectSpawned.getGameObject().getId() == BANANA_GAME_OBJECT_ID)
		{
			playSound("stuge");
			bananaDelay = BANANA_DELAY_TICKS;
		}
	}

	/**
	 * Updates the raid state, since the invocation level is stored in a varbit.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		updateRaidState();
	}

	/**
	 * Updates the raid state based on the player's current region and the raid's invocation level.
	 * <p>
	 * The invocation level is only read while the player is in the Nexus, Apmeken or Ba-Ba rooms, and is remembered
	 * for the rest of the raid. Entering the lobby clears it. If the invocation level is 0, then they have not turned
	 * on any invocations, including jungle japes, or they are not in a raid, in which case the plugin will not be
	 * active for the sound effects.
	 */
	private void updateRaidState()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int region = getRegion();
		if (region == UNKNOWN_REGION)
		{
			return;
		}

		if (region == TOA_LOBBY_ID)
		{
			resetState();
			return;
		}

		if (region == TOA_NEXUS_ID || region == TOA_APMEKEN_ID || region == TOA_BABA_ID)
		{
			invocationLevel = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
		}

		inToaRaid = invocationLevel > 0;
	}

	/**
	 * Resets the raid state by setting their in raid status to false and the invocation level to 0.
	 */
	private void resetState()
	{
		invocationLevel = 0;
		inToaRaid = false;
	}

	/**
	 * Gets the map region ID of the local player's location.
	 *
	 * @return Map region ID, or {@link #UNKNOWN_REGION} if the local player is not available (e.g. while loading).
	 */
	private int getRegion()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return UNKNOWN_REGION;
		}

		LocalPoint localPoint = localPlayer.getLocalLocation();
		return WorldPoint.fromLocalInstance(client, localPoint).getRegionID();
	}

	/**
	 * Plays a sound effect. Loads the .wav audio files.
	 *
	 * @param audio - "stuge" or "rallittelija" depending on which sound effect is desired.
	 */
	private void playSound(String audio)
	{
		String soundFile = "/" + audio + ".wav";
		closeClip();

		try (InputStream input = JungleJapesPlugin.class.getResourceAsStream(soundFile))
		{
			if (input == null)
			{
				log.warn("Audio file not found: {}", soundFile);
				return;
			}

			// BufferedInputStream supports the optional mark/reset for AudioInputStream.
			try (AudioInputStream sound = AudioSystem.getAudioInputStream(new BufferedInputStream(input)))
			{
				clip = AudioSystem.getClip();
				clip.open(sound);
			}
		}
		catch (UnsupportedAudioFileException | IOException | LineUnavailableException | IllegalArgumentException e)
		{
			log.warn("Could not load the sound file {}: ", soundFile, e);
			closeClip();
			return;
		}

		setVolume(clip);
		clip.start();
	}

	/**
	 * Applies the configured volume to the clip, clamped to what the audio line supports.
	 */
	private void setVolume(Clip target)
	{
		if (!target.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			return;
		}

		FloatControl volume = (FloatControl) target.getControl(FloatControl.Type.MASTER_GAIN);
		float gain = config.volume() - 100;

		volume.setValue(Math.max(volume.getMinimum(), Math.min(volume.getMaximum(), gain)));
	}

	/**
	 * Closes the current clip, if any.
	 */
	private void closeClip()
	{
		if (clip == null)
		{
			return;
		}

		clip.close();
		clip = null;
	}

	/**
	 * Provides the plugin's config to RuneLite.
	 */
	@Provides
	JungleJapesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(JungleJapesConfig.class);
	}
}
