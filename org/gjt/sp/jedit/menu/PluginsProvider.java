/*
 * PluginsProvider.java - Plugins menu
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2003 Slava Pestov
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

package org.gjt.sp.jedit.menu;

import javax.swing.*;
import java.util.*;
import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

public class PluginsProvider implements DynamicMenuProvider
{
	//{{{ updateEveryTime() method
	public boolean updateEveryTime()
	{
		return false;
	} //}}}

	//{{{ update() method
	public void update(JMenu menu)
	{
		// We build a set of lists, each list contains plugin menu
		// items that begin with a given letter.
		int count = 0;

		List<List<JMenuItem>> letters = new ArrayList<List<JMenuItem>>(26);
		for(int i = 0; i < 26; i++)
		{
			letters.add(new ArrayList<JMenuItem>());
		}

		PluginJAR[] pluginArray = jEdit.getPluginJARs();
		for (PluginJAR jar : pluginArray)
		{
			EditPlugin plugin = jar.getPlugin();
			if (plugin == null)
				continue;

			JMenuItem menuItem = plugin.createMenuItems();
			if (menuItem != null)
			{
				addToLetterMap(letters, menuItem);
				count++;
			}
		}

		if(count == 0)
		{
			JMenuItem menuItem = new JMenuItem(
				jEdit.getProperty("no-plugins.label"));
			menuItem.setEnabled(false);
			menu.add(menuItem);
			return;
		}

		// Sort each letter
		for (List<JMenuItem> letter1 : letters)
			Collections.sort(letter1, new MenuItemTextComparator());

		int maxItems = jEdit.getIntegerProperty("menu.spillover", 20);

		// if less than 20 items, put them directly in the menu
		if(count <= maxItems)
		{
			for (List<JMenuItem> items : letters)
			{
				for (JMenuItem item : items)
					menu.add(item);
			}

			return;
		}

		// Collect blocks of up to maxItems of consecutive letters
		count = 0;
		char first = 'A';
		JMenu submenu = new JMenu();
		menu.add(submenu);

		for(int i = 0; i < letters.size(); i++)
		{
			List<JMenuItem> letter = letters.get(i);

			if(count + letter.size() > maxItems && count != 0)
			{
				char last = (char)(i + 'A' - 1);
				if(last == first)
					submenu.setText(String.valueOf(first));
				else
					submenu.setText(first + " - " + last);
				first = (char)(i + 'A');
				count = 0;
				submenu = null;
			}

			for (JMenuItem item : letter)
			{
				if (submenu == null)
				{
					submenu = new JMenu();
					menu.add(submenu);
				}
				submenu.add(item);
			}

			count += letter.size();
		}

		if(submenu != null)
		{
			char last = 'Z';
			if(last == first)
				submenu.setText(String.valueOf(first));
			else
				submenu.setText(first + " - " + last);
		}
	} //}}}

	//{{{ addToLetterMap() method
	private void addToLetterMap(List<List<JMenuItem>> letters, JMenuItem item)
	{
		char ch = item.getText().charAt(0);
		ch = Character.toUpperCase(ch);
		if(ch < 'A' || ch > 'Z')
		{
			Log.log(Log.ERROR,this,"Plugin menu item label must "
				+ "begin with A - Z, or a - z: "
				+ item.getText());
		}
		else
			letters.get(ch - 'A').add(item);
	} //}}}
}
