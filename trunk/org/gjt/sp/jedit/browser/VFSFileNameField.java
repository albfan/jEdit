/*
 * VFSFileNameField.java - File name field with completion
 * :tabSize=8:indentSize=8:noTabs=false:
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

package org.gjt.sp.jedit.browser;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.io.File;
import org.gjt.sp.jedit.gui.HistoryTextField;
import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.MiscUtilities;

/**
 * @author Slava Pestov
 * @version $Id$
 * @since jEdit 4.2pre1
 */
class VFSFileNameField extends HistoryTextField
{
	//{{{ VFSFileNameField constructor
	VFSFileNameField(VFSBrowser browser, String model)
	{
		super(model);
		setEnterAddsToHistory(false);

		this.browser = browser;

		Dimension dim = getPreferredSize();
		dim.width = Integer.MAX_VALUE;
		setMaximumSize(dim);
	} //}}}

	//{{{ isManagingFocus() method
	public boolean isManagingFocus()
	{
		return false;
	} //}}}

	//{{{ getFocusTraversalKeysEnabled() method
	public boolean getFocusTraversalKeysEnabled()
	{
		return false;
	} //}}}

	//{{{ processKeyEvent() method
	public void processKeyEvent(KeyEvent evt)
	{
		if(evt.getID() == KeyEvent.KEY_PRESSED)
		{
			String path = getText();

			switch(evt.getKeyCode())
			{
			case KeyEvent.VK_TAB:
				doComplete(path);
				break;
			case KeyEvent.VK_LEFT:
				if(getCaretPosition() == 0)
					browser.getBrowserView().getTable().processKeyEvent(evt);
				else
					super.processKeyEvent(evt);
				break;
			case KeyEvent.VK_RIGHT:
				if(getCaretPosition() == getDocument().getLength())
					browser.getBrowserView().getTable().processKeyEvent(evt);
				else
					super.processKeyEvent(evt);
				break;
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
			case KeyEvent.VK_PAGE_UP:
			case KeyEvent.VK_PAGE_DOWN:
			case KeyEvent.VK_ENTER:
				browser.getBrowserView().getTable().processKeyEvent(evt);
				break;
			default:
				super.processKeyEvent(evt);
				break;
			}
		}
		else if(evt.getID() == KeyEvent.KEY_TYPED)
		{
			char ch = evt.getKeyChar();
			if(ch == '/' || ch == File.separatorChar)
			{
				super.processKeyEvent(evt);
				String path = getText();

				if(path.length() == 2 && path.charAt(0) == '-')
				{
					path = browser.getView().getBuffer()
						.getDirectory();
				}
				else if(path.length() == 3 && path.startsWith(".."))
				{
					path = MiscUtilities.getParentOfPath(
						browser.getDirectory());
					VFS vfs = VFSManager.getVFSForPath(path);
					if((vfs.getCapabilities() & VFS.LOW_LATENCY_CAP) != 0)
					{
						browser.setDirectory(path);
						VFSManager.waitForRequests();
						setText(null);
					}
				}
				else if(!MiscUtilities.isAbsolutePath(path))
				{
					VFS.DirectoryEntry[] files = browser
						.getBrowserView().getSelectedFiles();
					if(files.length != 1
						|| files[0].type ==
						VFS.DirectoryEntry.FILE)
					{
						return;
					}
					path = files[0].path;
				}

				VFS vfs = VFSManager.getVFSForPath(path);
				if((vfs.getCapabilities() & VFS.LOW_LATENCY_CAP) != 0)
				{
					setText(null);
					browser.setDirectory(path);
					VFSManager.waitForRequests();
				}
				else
				{
					if(path.endsWith("/") || path.endsWith(File.separator))
						setText(path);
					else
						setText(path + vfs.getFileSeparator());
				}
			}
			else if(ch == '\b')
			{
				if(getCaretPosition() == 0)
				{
					goToParent();

					return;
				}
				else
				{
					super.processKeyEvent(evt);
					String path = getText();

					BrowserView view = browser.getBrowserView();
					view.selectNone();
					view.getTable().doTypeSelect(path,true);
				}
			}
			else if(ch > 0x20 && ch != 0x7f && ch != 0xff)
			{
				super.processKeyEvent(evt);
				String path = getText();

				BrowserView view = browser.getBrowserView();
				view.selectNone();
				view.getTable().doTypeSelect(path,true);
			}
			else
				super.processKeyEvent(evt);
		}
	} //}}}

	//{{{ Private members
	private VFSBrowser browser;

	//{{{ doComplete() method
	private void doComplete(String currentText)
	{
		BrowserView view = browser.getBrowserView();
		view.selectNone();
		view.getTable().doTypeSelect(currentText,false);

		VFS.DirectoryEntry[] files = view.getSelectedFiles();
		if(files.length == 0)
			return;

		String path = files[0].path;
		String name = files[0].name;
		String parent = MiscUtilities.getParentOfPath(path);

		String newText;
		if(MiscUtilities.isAbsolutePath(currentText)
			&& !currentText.startsWith(browser.getDirectory()))
		{
			newText = path;
		}
		else
		{
			if(pathsEqual(parent,browser.getDirectory()))
				newText = name;
			else
				newText = path;
		}

		setText(newText);
	} //}}}

	//{{{ pathsEqual() method
	private boolean pathsEqual(String p1, String p2)
	{
		if(p1.endsWith("/") || p1.endsWith(File.separator))
			p1 = p1.substring(0,p1.length() - 1);
		if(p2.endsWith("/") || p2.endsWith(File.separator))
			p2 = p2.substring(0,p2.length() - 2);
		return p1.equals(p2);
	} //}}}

	//{{{ goToParent() method
	private void goToParent()
	{
		String name = MiscUtilities.getFileName(browser.getDirectory());
		if(name.endsWith("/") || name.endsWith(File.separator))
			name = name.substring(0,
			name.length() - 1);
		String parent = MiscUtilities.getParentOfPath(
			browser.getDirectory());
		browser.setDirectory(parent);

		VFS vfs = VFSManager.getVFSForPath(parent);
		if((vfs.getCapabilities() & VFS.LOW_LATENCY_CAP) != 0)
		{
			VFSManager.waitForRequests();
			setText(name);
			browser.getBrowserView().getTable().doTypeSelect(
				name,true);
		}
	} //}}}

	//}}}
}
