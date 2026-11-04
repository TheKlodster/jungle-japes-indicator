package com.theklodster.code;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("Jungle Japes Indicator")
public interface JungleJapesConfig extends Config
{
	@Range(
		max = 100
	)
	@ConfigItem(
		keyName = "Volume",
		name = "Volume",
		description = "The volume you play the sound effect."
	)
	default int volume()
	{
		return 100;
	}
}
