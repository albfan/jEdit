/*
 * EnhancedDialog.java - Handles OK/Cancel for you
 * Copyright (C) 1998, 1999, 2001 Slava Pestov
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

import javax.swing.*;

import org.gjt.sp.jedit.GUIUtilities;

import java.awt.event.*;
import java.awt.*;

/** Dialog that handles OK/Cancel for you
 *
 * A dialog box that handles window closing, the ENTER key and the ESCAPE
 * key for you. All you have to do is implement ok() (called when
 * Enter is pressed) and cancel() (called when Escape is pressed, or window
 * is closed).
 * @author Slava Pestov
 * @version $Id$
 */
public abstract class EnhancedDialog extends JDialog
{
	protected EnhancedDialog(Frame parent, String title, boolean modal)
	{
		super(parent,title,modal);
		_init();
	}

    protected EnhancedDialog(Dialog parent, String title, boolean modal)
	{
		super(parent,title,modal);
		_init();
	}

	public boolean getEnterEnabled()
	{
		return enterEnabled;
	}

	public void setEnterEnabled(boolean enterEnabled)
	{
		this.enterEnabled = enterEnabled;
	}

	public abstract void ok();
	public abstract void cancel();

	//{{{ Private members
	private void _init()
	{
		((Container)getLayeredPane()).addContainerListener(
                new ContainerHandler());
		getContentPane().addContainerListener(new ContainerHandler());

		keyHandler = new KeyHandler();
		addKeyListener(keyHandler);
		addWindowListener(new WindowHandler());

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		enterEnabled = true;
	}
	//}}}

	// protected members
	protected KeyHandler keyHandler;
	protected boolean enterEnabled;

	// Recursively adds our key listener to sub-components
	class ContainerHandler extends ContainerAdapter
	{
		@Override
        public void componentAdded(ContainerEvent evt)
		{
			componentAdded(evt.getChild());
		}

		@Override
        public void componentRemoved(ContainerEvent evt)
		{
			componentRemoved(evt.getChild());
		}

		private void componentAdded(Component comp)
		{
			comp.addKeyListener(keyHandler);
			if(comp instanceof Container)
			{
				Container cont = (Container)comp;
				cont.addContainerListener(this);
				Component[] comps = cont.getComponents();
				for (Component comp1 : comps)
					componentAdded(comp1);
			}
		}

		private void componentRemoved(Component comp)
		{
			comp.removeKeyListener(keyHandler);
			if(comp instanceof Container)
			{
				Container cont = (Container)comp;
				cont.removeContainerListener(this);
				Component[] comps = cont.getComponents();
				for (Component comp1 : comps)
					componentRemoved(comp1);
			}
		}
	}

	class KeyHandler extends KeyAdapter
	{
		@Override
        public void keyPressed(KeyEvent evt)
		{
			if(evt.isConsumed()) return;
			Component comp = getFocusOwner();
			if(evt.getKeyCode() == KeyEvent.VK_ENTER && enterEnabled)
			{
				while(comp != null)
				{
					if(comp instanceof JComboBox)
					{
						JComboBox<?> combo = (JComboBox<?>)comp;
						if(combo.isEditable())
						{
							Object selected = combo.getEditor().getItem();
							if(selected != null)
								combo.setSelectedItem(selected);
						}

						if(combo.isPopupVisible())
						{
							evt.consume();
							combo.setPopupVisible(false);
						}
						return;
					}
					// TODO: add other classes that need custom key handling here.
					comp = comp.getParent();
				}
				evt.consume();
				ok();
			}
			else if (evt.getKeyCode() == KeyEvent.VK_ESCAPE ||
					isCloseBufferShortcut(evt))
			{
				evt.consume();
				if (comp instanceof JComboBox)
				{
					JComboBox<?> combo = (JComboBox<?>)comp;
					if (combo.isPopupVisible())
						combo.setPopupVisible(false);
					else cancel();
				}
				else
					cancel();
			}
		}

		private boolean isCloseBufferShortcut(KeyEvent evt)
		{
			String shortcut = GUIUtilities.getShortcutLabel("close-buffer", false);
			if (shortcut == null) return false;
			String[] s = shortcut.split(" or ");
			if(s.length == 1)
			{ // w/o alternative shortcut
				if(s[0].contains(" "))  //primary shortcut is a multiple-key shortcut
					return false;
				else
					return KeyEventTranslator.parseKey(s[0]).equals(KeyEventTranslator.translateKeyEvent(evt));
			} // w/ alternative shortcut
			boolean primarymatch,altmatch;
			primarymatch=altmatch=false;

			if(!s[0].contains(" "))
				primarymatch = KeyEventTranslator.parseKey(s[0]).equals(KeyEventTranslator.translateKeyEvent(evt));

			if(!primarymatch && !s[1].contains(" "))
				altmatch = KeyEventTranslator.parseKey(s[1]).equals(KeyEventTranslator.translateKeyEvent(evt));

			if (primarymatch || altmatch)
				return true;
			return false;
		}
	}

	class WindowHandler extends WindowAdapter
	{
		@Override
        public void windowClosing(WindowEvent evt)
		{
			cancel();
		}
	}
}
