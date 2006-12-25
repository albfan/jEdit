/*
 * ReloadWithEncodingProvider.java - Recent file list menu
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2006 Marcelo Vanzin
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import java.util.Arrays;
import java.util.Hashtable;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;

/**
 * Menu provider for actions to reload the current buffer with a
 * specific encoding.
 *
 * @version $Id$
 */
public class ReloadWithEncodingProvider
		implements ActionListener, DynamicMenuProvider

{

	private View view;

	public boolean updateEveryTime()
	{
		return false;
	}

	public void update(JMenu menu)
	{
		view = GUIUtilities.getView(menu);

		// auto detect
		JMenuItem auto = new JMenuItem(
			jEdit.getProperty("vfs.browser.commands.encoding.auto-detect"));
		auto.setActionCommand("auto-detect");
		auto.addActionListener(this);
		menu.add(auto);
		menu.addSeparator();

		// all the enabled encodings + the system encoding
		String[] encodings = MiscUtilities.getEncodings(true);
		String systemEncoding = System.getProperty("file.encoding");

		if (Arrays.binarySearch(encodings, systemEncoding) < 0) {
			String[] tmp_a = new String[encodings.length + 1];
			System.arraycopy(encodings, 0, tmp_a, 0, encodings.length);
			tmp_a[encodings.length] = systemEncoding;
			encodings = tmp_a;
		}

		Arrays.sort(encodings);

		for (int i = 0; i < encodings.length; i++)
		{
			JMenuItem mi = new JMenuItem(encodings[i]);
			mi.setActionCommand("encoding@" + encodings[i]);
			mi.addActionListener(this);
			menu.add(mi);
		}

		menu.addSeparator();

		// option to prompt for the encoding
		JMenuItem other = new JMenuItem(
			jEdit.getProperty("vfs.browser.other-encoding.label"));
		other.setActionCommand("other-encoding");
		other.addActionListener(this);
		menu.add(other);

	}

	public void actionPerformed(ActionEvent ae)
	{
		JMenuItem mi = (JMenuItem) ae.getSource();
		String action = mi.getActionCommand();
		String encoding = null;
		Hashtable props = null;

		if (action.startsWith("encoding@"))
		{
			encoding = action.substring(9);
		}
		else if (action.equals("other-encoding"))
		{
			encoding = JOptionPane.showInputDialog(view,
				jEdit.getProperty("encoding-prompt.message"),
				jEdit.getProperty("encoding-prompt.title"),
				JOptionPane.QUESTION_MESSAGE);
			if (encoding == null)
				return;

			try
			{
				Charset.forName(encoding);
			}
			catch (UnsupportedCharsetException uce)
			{
				String msg = jEdit.getProperty("reload-encoding.error",
						new Object[] { encoding });
				JOptionPane.showMessageDialog(view,
					msg,
					jEdit.getProperty("common.error"),
					JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		if (encoding != null)
		{
			props = new Hashtable();
			props.put(Buffer.ENCODING, encoding);
		}

		String path = view.getBuffer().getPath();
		jEdit.closeBuffer(view, view.getBuffer());
		jEdit.openFile(view,null,path,false,props);
	}

}

