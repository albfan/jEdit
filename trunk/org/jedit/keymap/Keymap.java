/*
 * jEdit - Programmer's Text Editor
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2011 Matthieu Casanova
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.keymap;

/**
 * @author Matthieu Casanova
 * @since jEdit 5.0
 */
public interface Keymap
{
	/**
	 * Returns a shortcut.
	 * @param name the shortcut name
	 * @return the action name or <code>null</code> if there is no shortcut
	 */
	String getShortcut(String name);
	
	/**
	 * Set a new shortcut.
	 * @param name the shortcut name
	 * @param shortcut the action name, or <code>null</code> to delete a 
	 * shortcut
	 */
	void setShortcut(String name, String shortcut);
	
	/**
	 * Save the keymaps.
	 */
	void save();
}
