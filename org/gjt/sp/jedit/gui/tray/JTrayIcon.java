/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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

package org.gjt.sp.jedit.gui.tray;

import org.gjt.sp.jedit.GUIUtilities;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Matthieu Casanova
 */
public class JTrayIcon extends TrayIcon
{
	private JDialog parent;
	private JPopupMenu menu;
	private MouseListener mouseListener;
	private PopupMenuListener popupMenuListener;

	public JTrayIcon(Image image, String tooltip)
	{
		super(image, tooltip, null);

	}

	public JPopupMenu getMenu()
	{
		return menu;
	}

	public void setMenu(JPopupMenu menu)
	{
		if (menu == null)
		{

			if (mouseListener != null)
			{
				removeMouseListener(mouseListener);
				mouseListener = null;
			}
			if (popupMenuListener != null)
			{
				this.menu.removePopupMenuListener(popupMenuListener);
				popupMenuListener = null;
			}
			parent = null;
		}
		else
		{
			parent = new JDialog((Frame) null);
			parent.setUndecorated(true);
			parent.setAlwaysOnTop(true);
			if (mouseListener == null)
			{
				mouseListener = new MyMouseListener();
				addMouseListener(mouseListener);
			}
			popupMenuListener = new MyPopupMenuListener();
			menu.addPopupMenuListener(popupMenuListener);
		}
		this.menu = menu;
	}

	private class MyMouseListener extends MouseAdapter
	{
		@Override
		public void mouseClicked(MouseEvent e)
		{
			if (GUIUtilities.isPopupTrigger(e))
			{
				parent.setLocation(e.getX(), e.getY() - menu.getPreferredSize().height);
				parent.setVisible(true);
				menu.show(parent, 0, 0);
			}
		}
	}

	private class MyPopupMenuListener implements PopupMenuListener
	{
		@Override
		public void popupMenuWillBecomeVisible(PopupMenuEvent e)
		{
		}

		@Override
		public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
		{
			parent.setVisible(false);
		}

		@Override
		public void popupMenuCanceled(PopupMenuEvent e)
		{
			parent.setVisible(false);
		}
	}
}
