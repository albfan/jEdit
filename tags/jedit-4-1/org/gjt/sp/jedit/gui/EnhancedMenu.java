/*
 * EnhancedMenu.java - jEdit menu
 * :tabSize=8:indentSize=8:noTabs=false:
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

package org.gjt.sp.jedit.gui;

//{{{ Imports
import javax.swing.event.*;
import javax.swing.*;
import java.util.StringTokenizer;
import org.gjt.sp.jedit.*;
//}}}

public class EnhancedMenu extends JMenu implements MenuListener
{
	//{{{ EnhancedMenu constructor
	public EnhancedMenu(String name)
	{
		this._name = name;
		String label = jEdit.getProperty(name.concat(".label"));
		if(label == null)
			label = name;

		char mnemonic;
		int index = label.indexOf('$');
		if(index != -1 && label.length() - index > 1)
		{
			mnemonic = Character.toLowerCase(label.charAt(index + 1));
			label = label.substring(0,index).concat(label.substring(++index));
		}
		else
			mnemonic = '\0';

		setText(label);
		if(!OperatingSystem.isMacOS())
			setMnemonic(mnemonic);

		addMenuListener(this);
		//init();
	} //}}}

	//{{{ menuSelected() method
	public void menuSelected(MenuEvent evt)
	{
		init();
	} //}}}

	public void menuDeselected(MenuEvent e) {}

	public void menuCanceled(MenuEvent e) {}

	//{{{ init() method
	public void init()
	{
		if(initialized)
			return;

		initialized = true;

		String menuItems = jEdit.getProperty(_name);
		if(menuItems != null)
		{
			StringTokenizer st = new StringTokenizer(menuItems);
			while(st.hasMoreTokens())
			{
				String menuItemName = st.nextToken();
				if(menuItemName.equals("-"))
					addSeparator();
				else
				{
					if(menuItemName.startsWith("%"))
						add(GUIUtilities.loadMenu(menuItemName.substring(1)));
					else
						add(GUIUtilities.loadMenuItem(menuItemName));
				}
			}
		}
	} //}}}

	protected String _name;
	protected boolean initialized;
}
