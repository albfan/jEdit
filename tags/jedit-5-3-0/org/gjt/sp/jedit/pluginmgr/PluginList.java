/*
 * PluginList.java - Plugin list downloaded from server
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2001, 2003 Slava Pestov
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.gjt.sp.jedit.pluginmgr;

//{{{ Imports
import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.gjt.sp.util.*;
import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.XMLReaderFactory;
import org.gjt.sp.jedit.*;
//}}}


/**
 * Plugin list downloaded from server.
 * @since jEdit 3.2pre2
 * @version $Id$
 */
class PluginList
{
	/**
	 * Magic numbers used for auto-detecting GZIP files.
	 */
	public static final int GZIP_MAGIC_1 = 0x1f;
	public static final int GZIP_MAGIC_2 = 0x8b;
	public static final long MILLISECONDS_PER_MINUTE = 60L * 1000L;

	final List<Plugin> plugins = new ArrayList<>();
	final Map<String, Plugin> pluginHash = new HashMap<>();
	final List<PluginSet> pluginSets = new ArrayList<>();

	/**
	 * The mirror id.
	 * @since jEdit 4.3pre3
	 */
	private final String id;
	private String cachedURL;
	private final Task task;
	String gzipURL;

	PluginList(Task task)
	{
		id = jEdit.getProperty("plugin-manager.mirror.id");
		this.task = task;
		readPluginList(true);
	}

	void readPluginList(boolean allowRetry)
	{
		String mirror = buildMirror(id);
		if (mirror == null)
			return;
		gzipURL = jEdit.getProperty("plugin-manager.export-url");
		gzipURL += "?mirror=" + mirror;
		String path = null;
		if (jEdit.getSettingsDirectory() == null)
		{
			cachedURL = gzipURL;
		}
		else
		{
			path = jEdit.getSettingsDirectory() + File.separator + "pluginMgr-Cached.xml.gz";
			cachedURL = "file:///" + path;
		}
		boolean downloadIt = !id.equals(jEdit.getProperty("plugin-manager.mirror.cached-id"));
		if (path != null)
		{
			try
			{

				File f = new File(path);
				if (!f.canRead()) downloadIt = true;
				long currentTime = System.currentTimeMillis();
				long age = currentTime - f.lastModified();
				/* By default only download plugin lists every 5 minutes */
				long interval = jEdit.getIntegerProperty("plugin-manager.list-cache.minutes", 5) * MILLISECONDS_PER_MINUTE;
				if (age > interval)
				{
					Log.log(Log.MESSAGE, this, "PluginList cached copy too old. Downloading from mirror. ");
					downloadIt = true;
				}
			}
			catch (Exception e)
			{
				Log.log(Log.MESSAGE, this, "No cached copy. Downloading from mirror. ");
				downloadIt = true;
			}
		}
		if (downloadIt && cachedURL != gzipURL)
		{
			downloadPluginList();
		}
		InputStream in = null, inputStream = null;
		try
		{
			if (cachedURL != gzipURL)
				Log.log(Log.MESSAGE, this, "Using cached pluginlist");
			inputStream = new URL(cachedURL).openStream();
			XMLReader parser = XMLReaderFactory.createXMLReader();
			PluginListHandler handler = new PluginListHandler(this, cachedURL);
			in = new BufferedInputStream(inputStream);
			if(in.markSupported())
			{
				in.mark(2);
				int b1 = in.read();
				int b2 = in.read();
				in.reset();

				if(b1 == GZIP_MAGIC_1 && b2 == GZIP_MAGIC_2)
					in = new GZIPInputStream(in);
			}
			InputSource isrc = new InputSource(new InputStreamReader(in,"UTF8"));
			isrc.setSystemId("jedit.jar");
			parser.setContentHandler(handler);
			parser.setDTDHandler(handler);
			parser.setEntityResolver(handler);
			parser.setErrorHandler(handler);
			parser.parse(isrc);

		}
		catch (Exception e)
		{
			Log.log(Log.ERROR, this, "readpluginlist: error", e);
			if (cachedURL.startsWith("file:///"))
			{
				Log.log(Log.DEBUG, this, "Unable to read plugin list, deleting cached file and try again");
				new File(cachedURL.substring(8)).delete();
				if (allowRetry)
				{
					plugins.clear();
					pluginHash.clear();
					pluginSets.clear();
					readPluginList(false);
				}
			}
		}
		finally
		{
			IOUtilities.closeQuietly((Closeable)in);
			IOUtilities.closeQuietly((Closeable)inputStream);
		}

	}

	/** Caches it locally */
	void downloadPluginList()
	{
		BufferedInputStream is = null;
		BufferedOutputStream out = null;
		/* download the plugin list, while trying to show informative error messages.
		 * Currently when :
		 * - the proxy requires authentication
		 * - another HTTP error happens (may be good to know that the site is broken)
		 * - the host can't be reached (reported as internet access error)
		 * Otherwise, only an error message is logged in the activity log.
		 **/
		try
		{

			task.setStatus(jEdit.getProperty("plugin-manager.list-download"));
			URL downloadURL = new URL(gzipURL);
			HttpURLConnection c = (HttpURLConnection)downloadURL.openConnection();
			if(c.getResponseCode() == HttpURLConnection.HTTP_PROXY_AUTH)
			{
				GUIUtilities.error(jEdit.getActiveView()
					, "plugin-manager.list-download.need-password"
					, new Object[]{});
				Log.log (Log.ERROR, this, "CacheRemotePluginList: proxy requires authentication");
			}
			else if(c.getResponseCode() == HttpURLConnection.HTTP_OK)
			{
				InputStream inputStream = c.getInputStream();
				String fileName = cachedURL.replaceFirst("file:///", "");
				out = new BufferedOutputStream(new FileOutputStream(fileName));
				long start = System.currentTimeMillis();
				is = new BufferedInputStream(inputStream);
				IOUtilities.copyStream(4096, null, is, out, false);
				jEdit.setProperty("plugin-manager.mirror.cached-id", id);
				Log.log(Log.MESSAGE, this, "Updated cached pluginlist " + (System.currentTimeMillis() - start));
			}
			else
			{
				GUIUtilities.error(jEdit.getActiveView()
					, "plugin-manager.list-download.generic-error"
					, new Object[]{c.getResponseCode(), c.getResponseMessage()});
				Log.log (Log.ERROR, this, "CacheRemotePluginList: HTTP error: "+c.getResponseCode()+ c.getResponseMessage());
			}
		}
		catch(java.net.UnknownHostException e)
		{
				GUIUtilities.error(jEdit.getActiveView()
					, "plugin-manager.list-download.disconnected"
					, new Object[]{e.getMessage()});
			Log.log (Log.ERROR, this, "CacheRemotePluginList: error", e);
		}
		catch (Exception e)
		{
			Log.log (Log.ERROR, this, "CacheRemotePluginList: error", e);
		}
		finally
		{
			IOUtilities.closeQuietly((Closeable)out);
			IOUtilities.closeQuietly((Closeable)is);
		}
	}

	//{{{ addPlugin() method
	void addPlugin(Plugin plugin)
	{
		plugins.add(plugin);
		pluginHash.put(plugin.name,plugin);
	} //}}}

	//{{{ addPluginSet() method
	void addPluginSet(PluginSet set)
	{
		pluginSets.add(set);
	} //}}}

	//{{{ finished() method
	void finished()
	{
		// after the entire list is loaded, fill out plugin field
		// in dependencies
		for (Plugin plugin : plugins)
		{
			for (int j = 0; j < plugin.branches.size(); j++)
			{
				Branch branch = plugin.branches.get(j);
				for (int k = 0; k < branch.deps.size(); k++)
				{
					Dependency dep = branch.deps.get(k);
					if (dep.what.equals("plugin")) dep.plugin = pluginHash.get(dep.pluginName);
				}
			}
		}
	} //}}}

	//{{{ dump() method
	void dump()
	{
		for (Plugin plugin : plugins)
		{
			System.err.println(plugin);
			System.err.println();
		}
	} //}}}

	//{{{ getMirrorId() method
	/**
	 * Returns the mirror ID.
	 *
	 * @return the mirror ID
	 * @since jEdit 4.3pre3
	 */
	String getMirrorId()
	{
		return id;
	} //}}}

	//{{{ PluginSet class
	static class PluginSet
	{
		String name;
		final List<String> plugins = new ArrayList<>();

		public String toString()
		{
			return plugins.toString();
		}
	} //}}}

	//{{{ Plugin class
	public static class Plugin
	{
		String jar;
		String name;
		String description;
		String author;
		final List<Branch> branches = new ArrayList<>();
		String installedVersion = null;
		String installedPath = null;
		boolean loaded = false;

		String getInstalledVersion()
		{
			this.loaded = false;
			PluginJAR[] jars = jEdit.getPluginJARs();
			for(int i = 0; i < jars.length; i++)
			{
				String path = jars[i].getPath();

				if(MiscUtilities.getFileName(path).equals(jar))
				{
					EditPlugin plugin = jars[i].getPlugin();
					if(plugin != null)
					{
						installedVersion = jEdit.getProperty(
							"plugin." + plugin.getClassName()
							+ ".version");
						this.loaded = true;
						return installedVersion;
					}
					else
						return null;
				}
			}
			String[] notLoadedJars = jEdit.getNotLoadedPluginJARs();
			for(String path: notLoadedJars){
				if(MiscUtilities.getFileName(path).equals(jar))
				{
					try
					{
						PluginJAR.PluginCacheEntry cacheEntry = PluginJAR.getPluginCacheEntry(path);
						if(cacheEntry != null)
						{
							String versionKey = "plugin." + cacheEntry.pluginClass + ".version";
							installedVersion = cacheEntry.cachedProperties.getProperty(versionKey);
							Log.log(Log.DEBUG, PluginList.class, "found installed but not loaded "+ jar + " version=" + installedVersion);
							installedPath = path; 
							return installedVersion;
						}
					}
					catch (IOException e)
					{
						Log.log(Log.WARNING, "Unable to access cache for "+jar, e);
					}
				}
			}

			return null;
		}

		String getInstalledPath()
		{
			if(installedPath != null){
				if(new File(installedPath).exists()){
					return installedPath;
				}else{
					installedPath = null;
				}
			}

			PluginJAR[] jars = jEdit.getPluginJARs();
			for(int i = 0; i < jars.length; i++)
			{
				String path = jars[i].getPath();

				if(MiscUtilities.getFileName(path).equals(jar))
					return path;
			}

			return null;
		}

		/**
		 * Find the first branch compatible with the running jEdit release.
		 */
		Branch getCompatibleBranch()
		{
			for (Branch branch : branches)
			{
				if (branch.canSatisfyDependencies())
					return branch;
			}

			return null;
		}

		boolean canBeInstalled()
		{
			Branch branch = getCompatibleBranch();
			return branch != null && !branch.obsolete
				&& branch.canSatisfyDependencies();
		}

		void install(Roster roster, String installDirectory, boolean downloadSource, boolean asDependency)
		{
			String installed = getInstalledPath();

			Branch branch = getCompatibleBranch();
			if(branch.obsolete)
			{
				if(installed != null)
					roster.addRemove(installed);
				return;
			}

			//branch.satisfyDependencies(roster,installDirectory,
			//	downloadSource);

			if(installedVersion != null && installedPath!= null && !loaded && asDependency)
			{
				roster.addLoad(installedPath);
				return;
			}

			if(installed != null)
			{
				installDirectory = MiscUtilities.getParentOfPath(
					installed);
			}

			roster.addInstall(
				installed,
				downloadSource ? branch.downloadSource : branch.download,
				installDirectory,
				downloadSource ? branch.downloadSourceSize : branch.downloadSize);

		}

		public String toString()
		{
			return name;
		}
	} //}}}

	//{{{ Branch class
	static class Branch
	{
		String version;
		String date;
		int downloadSize;
		String download;
		int downloadSourceSize;
		String downloadSource;
		boolean obsolete;
		final List<Dependency> deps = new ArrayList<>();

		boolean canSatisfyDependencies()
		{
			for (Dependency dep : deps)
			{
				if (!dep.canSatisfy())
					return false;
			}

			return true;
		}

		void satisfyDependencies(Roster roster, String installDirectory,
			boolean downloadSource)
		{
			for (Dependency dep : deps)
				dep.satisfy(roster, installDirectory, downloadSource);
		}
		
		public String depsToString() 
		{
			StringBuilder sb = new StringBuilder();
			for (Dependency dep : deps) 
			{
				if ("plugin".equals(dep.what) && dep.pluginName != null) 
				{
					sb.append(dep.pluginName).append('\n');
				}
			}
			return sb.toString();
		}

		public String toString()
		{
			return "[version=" + version + ",download=" + download
				+ ",obsolete=" + obsolete + ",deps=" + deps + ']';
		}
	} //}}}

	//{{{ Dependency class
	static class Dependency
	{
		final String what;
		final String from;
		final String to;
		// only used if what is "plugin"
		final String pluginName;
		Plugin plugin;

		Dependency(String what, String from, String to, String pluginName)
		{
			this.what = what;
			this.from = from;
			this.to = to;
			this.pluginName = pluginName;
		}

		boolean isSatisfied()
		{
			if(what.equals("plugin"))
			{
				for(int i = 0; i < plugin.branches.size(); i++)
				{
					String installedVersion = plugin.getInstalledVersion();
					if(installedVersion != null
						&&
					(from == null || StandardUtilities.compareStrings(
						installedVersion,from,false) >= 0)
						&&
						(to == null || StandardUtilities.compareStrings(
						      installedVersion,to,false) <= 0))
					{
						return true;
					}
				}

				return false;
			}
			else if(what.equals("jdk"))
			{
				String javaVersion = System.getProperty("java.version").substring(0,3);

				if((from == null || StandardUtilities.compareStrings(
					javaVersion,from,false) >= 0)
					&&
					(to == null || StandardUtilities.compareStrings(
						     javaVersion,to,false) <= 0))
					return true;
				else
					return false;
			}
			else if(what.equals("jedit"))
			{
				String build = jEdit.getBuild();

				if((from == null || StandardUtilities.compareStrings(
					build,from,false) >= 0)
					&&
					(to == null || StandardUtilities.compareStrings(
						     build,to,false) <= 0))
					return true;
				else
					return false;
			}
			else
			{
				Log.log(Log.ERROR,this,"Invalid dependency: " + what);
				return false;
			}
		}

		boolean canSatisfy()
		{
			if(isSatisfied())
				return true;
			if (what.equals("plugin"))
				return plugin.canBeInstalled();
			return false;
		}

		void satisfy(Roster roster, String installDirectory,
			boolean downloadSource)
		{
			if(what.equals("plugin"))
			{
				String installedVersion = plugin.getInstalledVersion();
				for(int i = 0; i < plugin.branches.size(); i++)
				{
					Branch branch = plugin.branches.get(i);
					if((installedVersion == null
						||
					StandardUtilities.compareStrings(
						installedVersion,branch.version,false) < 0)
						&&
					(from == null || StandardUtilities.compareStrings(
						branch.version,from,false) >= 0)
						&&
						(to == null || StandardUtilities.compareStrings(
						      branch.version,to,false) <= 0))
					{
						plugin.install(roster,installDirectory,
							downloadSource, false);
						return;
					}
				}
			}
		}

		public String toString()
		{
			return "[what=" + what + ",from=" + from
				+ ",to=" + to + ",plugin=" + plugin + ']';
		}
	} //}}}

	//{{{ Private members

	private static String buildMirror(String id)
	{
		if (id != null && !id.equals(MirrorList.Mirror.NONE))
		{
			return id;
		}
		try
		{
			return getAutoSelectedMirror();
		}
		catch (Exception e)
		{
			GUIUtilities.error(jEdit.getActiveView()
				, "plugin-manager.list-download.mirror-autoselect-error"
				, new Object[]{e});
			Log.log(Log.DEBUG, PluginList.class, "Getting auto-selected mirror: error", e);
		}
		return null;
	}

	private static String getAutoSelectedMirror()
		throws java.io.IOException
	{
		final String samplerUrl = "http://sourceforge.net/projects/jedit/files/latest/download";
		final HttpURLConnection connection = (HttpURLConnection)((new URL(samplerUrl)).openConnection());
		connection.setInstanceFollowRedirects(false);
		final int response = connection.getResponseCode();
		if (response != HttpURLConnection.HTTP_MOVED_TEMP)
		{
			throw new RuntimeException("Unexpected response: " + response + ": from " + samplerUrl);
		}
		final String redirected = connection.getHeaderField("Location");
		if (redirected == null)
		{
			throw new RuntimeException("Missing Location header: " + samplerUrl);
		}
		final String prefix = "use_mirror=";
		final int found = redirected.lastIndexOf(prefix);
		if (found == -1)
		{
			throw new RuntimeException("Mirror prefix \"use_mirror\" was not found in redirected URL: " + redirected);
		}
		final int start = found + prefix.length();
		final int end = redirected.indexOf('&', start);
		return end != -1 ?
			redirected.substring(start, end) :
			redirected.substring(start);
	}

	//}}}
}
