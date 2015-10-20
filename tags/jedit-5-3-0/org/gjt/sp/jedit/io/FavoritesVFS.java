/*
 * FavoritesVFS.java - Stores frequently-visited directory locations
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2000, 2004 Slava Pestov
 * Portions Copyright (C) 2011 Matthieu Casanova
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

package org.gjt.sp.jedit.io;

//{{{ Imports
import java.awt.Component;
import java.util.*;

import org.gjt.sp.jedit.msg.DynamicMenuChanged;
import org.gjt.sp.jedit.*;
//}}}

/**
 * A VFS used for remembering frequently-visited directories. Listing it
 * returns the favorites list. The deletePath of each entry is the
 * directory prefixed with "favorites:" so that right-clicking on a
 * favorite and clicking 'delete' in the browser just deletes the
 * favorite, and not the directory itself.
 * @author Slava Pestov
 * @version $Id$
 */
public class FavoritesVFS extends VFS
{
	public static final String PROTOCOL = "favorites";

	//{{{ FavoritesVFS constructor
	public FavoritesVFS()
	{
		super("favorites",DELETE_CAP | RENAME_CAP | LOW_LATENCY_CAP
			| NON_AWT_SESSION_CAP,
			new String[] { EA_TYPE });

		/* addToFavorites(), which is a static method
		 * (for convinience) needs an instance of the
		 * VFS to pass to VFSManager.sendVFSUpdate(),
		 * hence this hack. */
		instance = this;
	} //}}}

	//{{{ getParentOfPath() method
	@Override
	public String getParentOfPath(String path)
	{
		return PROTOCOL + ':';
	} //}}}

	//{{{ _listFiles() method
	@Override
	public VFSFile[] _listFiles(Object session, String url,
		Component comp)
	{
		return getFavorites();
	} //}}}

	//{{{ _getFile() method
	@Override
	public VFSFile _getFile(Object session, String path,
		Component comp)
	{
		// does it matter that this doesn't set the type correctly?
		return new Favorite(path,VFSFile.DIRECTORY);
	} //}}}

	//{{{ _delete() method
	@Override
	public boolean _delete(Object session, String path, Component comp)
	{
		synchronized(lock)
		{
			path = path.substring(PROTOCOL.length() + 1);

			Iterator<Favorite> iter = favorites.iterator();
			while(iter.hasNext())
			{
				if(iter.next().getPath().equals(path))
				{
					iter.remove();
					VFSManager.sendVFSUpdate(this,PROTOCOL
						+ ':',false);
					EditBus.sendAsync(new DynamicMenuChanged(
						"favorites"));
					return true;
				}
			}
		}

		return false;
	} //}}}

	//{{{ _delete() method

	/**
	 * Rename a favorite
	 * @param session no session needed you can give null
	 * @param from The old path (not the name)
	 * @param to the new name
	 * @param comp The component that will parent error dialog boxes
	 * @return true if the favorite having that old path exists
	 */
	@Override
	public boolean _rename(Object session, String from, String to, Component comp)
	{
		VFSFile[] favorites = getFavorites();
		for (VFSFile fav : favorites)
		{
			Favorite favorite = (Favorite) fav;
			if (favorite.getPath().equals(from))
			{
				favorite.label = to;
				return true;
			}
		}
		return false;
	}  //}}}

	//{{{ loadFavorites() method
	public static void loadFavorites()
	{
		synchronized(lock)
		{
			favorites = new LinkedList<Favorite>();

			String favoritePath;
			int i = 0;
			while((favoritePath = jEdit.getProperty("vfs.favorite." + i)) != null)
			{
				Favorite favorite = new Favorite(favoritePath,
					jEdit.getIntegerProperty("vfs.favorite."
					+ i + ".type",
								VFSFile.DIRECTORY));
				favorites.add(favorite);
				String label = jEdit.getProperty("vfs.favorite." + i + ".label");
				if (label != null)
				{
					favorite.label = label;
				}
				i++;
			}
		}
	} //}}}

	//{{{ addToFavorites() method
	public static void addToFavorites(String path, int type)
	{
		synchronized(lock)
		{
			if(favorites == null)
				loadFavorites();

			for (Favorite favorite : favorites)
			{
				if (favorite.getPath().equals(path))
					return;
			}

			favorites.add(new Favorite(path,type));

			VFSManager.sendVFSUpdate(instance,PROTOCOL + ':',false);
			EditBus.send(new DynamicMenuChanged("favorites"));
		}
	} //}}}

	//{{{ saveFavorites() method
	public static void saveFavorites()
	{
		synchronized(lock)
		{
			if(favorites == null)
				return;

			int i = 0;
			for (Favorite favorite : favorites)
			{
				String p = favorite.getPath();
				String l = favorite.getLabel();
				jEdit.setProperty("vfs.favorite." + i, p);
				if (p.equals(l) || MiscUtilities.abbreviate(p).equals(l))
					jEdit.unsetProperty("vfs.favorite." + i + ".label");
				else 
					jEdit.setProperty("vfs.favorite." + i + ".label", l);
				jEdit.setIntegerProperty("vfs.favorite." + i + ".type", favorite.getType());

				i++;
			}
			jEdit.unsetProperty("vfs.favorite." + favorites.size());
			jEdit.unsetProperty("vfs.favorite." + favorites.size()
				+ ".type");
		}
	} //}}}

	//{{{ getFavorites() method
	public static VFSFile[] getFavorites()
	{
		synchronized(lock)
		{
			if(favorites == null)
				loadFavorites();

			return favorites.toArray(
				new VFSFile[favorites.size()]);
		}
	} //}}}

	//{{{ Private members
	private static FavoritesVFS instance;
	private static final Object lock = new Object();
	private static List<Favorite> favorites;
	//}}}

	//{{{ Favorite class
	public static class Favorite extends VFSFile
	{
		private String label;

		Favorite(String path, int type)
		{
			super(path,path,PROTOCOL + ':' + path,type, 0L,false);
			this.label = MiscUtilities.abbreviateView(path);
		}

		public String getLabel()
		{
			return label;
		}

		@Override
		public String getExtendedAttribute(String name)
		{
			if(name.equals(EA_TYPE))
				return super.getExtendedAttribute(name);
			else
			{
				// don't want it to show "0 bytes" for size,
				// etc.
				return null;
			}
		}

		@Override
		public VFS getVFS()
		{
			return VFSManager.getVFSForProtocol(FavoritesVFS.PROTOCOL);
		}
	} //}}}
}
