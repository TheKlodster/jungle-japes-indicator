package com.theklodster;

import com.theklodster.code.JungleJapesPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class JungleJapesLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(JungleJapesPlugin.class);
		RuneLite.main(args);
	}
}