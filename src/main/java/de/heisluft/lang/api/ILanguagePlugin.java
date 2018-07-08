package de.heisluft.lang.api;

import de.heisluft.lang.Locale;

import java.io.File;
import java.util.List;

public interface ILanguagePlugin {

	/**
	 * Get whether a Locale will be provided after start / reload.
	 * This does <em>NOT</em> state, whether this file should be extracted or not.
	 * {@link Locale#EN_US EN_US} <b>MUST</b> be supported.
	 *
	 * @param locale the locale being checked
	 * @return whether the locale will exist
	 *
	 * @see #getExtractableFiles()
	 */
	boolean providesLocale(Locale locale);

	/**
	 * Get which Files should actually be extracted. If not overridden, HLS will try to extract the .lang file
	 * for <b>every</b> provided {@link Locale} (see {@link ILanguagePlugin#providesLocale(Locale)}).
	 *
	 * @return <code>null</code> to indicate all provided languages should be extracted,
	 * or a List containing all extractable lang file names
	 *
	 * @see #providesLocale(Locale)
	 */
	default List<String> getExtractableFiles() {
		return null;
	}

	/**
	 * Get the Jar File of your plugin. This method is added to avoid reflection (cause
	 * org.bukkit.plugin.java.JavaPlugin#getFile() is protected)
	 * @return
	 */
	File getPluginJarFile();
}
