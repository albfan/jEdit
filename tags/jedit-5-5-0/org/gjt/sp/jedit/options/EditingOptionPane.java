/*
 * EditingOptionPane.java - Mode-specific options panel
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1998, 2002 Slava Pestov
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

package org.gjt.sp.jedit.options;

//{{{ Imports
import javax.swing.*;

import java.awt.BorderLayout;
import java.awt.event.*;

import org.gjt.sp.jedit.*;
//}}}

/**
 * Panel to load three option panes into tabs: EditModesPane, ModeSettingsPane,
 * and UndoPane.
 * @author Dale Anson
 * @version $Id$
 */
public class EditingOptionPane extends AbstractOptionPane
{
	//{{{ EditingOptionPane constructor
	public EditingOptionPane()
	{
		super("editing");
		setLayout(new BorderLayout());
	} //}}}

	//{{{ _init() method
	@Override
	protected void _init()
	{
		JTabbedPane tabs = new JTabbedPane();
		modeSettings = new ModeSettingsPane();
		modeSettings._init();
		editModes = new EditModesPane();
		editModes._init();
		undoSettings = new UndoPane();
		undoSettings._init();
		tabs.addTab("Mode Settings", modeSettings);
		tabs.addTab("Edit Modes", editModes);
		tabs.addTab("Undo Settings", undoSettings);
		add(tabs);
	} //}}}
	

	//{{{ _save() method
	@Override
	protected void _save()
	{
		editModes._save();
		modeSettings._save();
		undoSettings._save();
	} //}}}

	//{{{ Private members

	//{{{ Instance variables
	EditModesPane editModes;
	ModeSettingsPane modeSettings;
	UndoPane undoSettings;
	//}}}

}
