/*
 * AutosaveBackupOptionPane.java - Autosave & backup options
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

package org.gjt.sp.jedit.options;

//{{{ Imports
import javax.swing.*;
import java.awt.event.*;
import java.util.StringTokenizer;
import org.gjt.sp.jedit.*;
//}}}

public class SaveBackupOptionPane extends AbstractOptionPane
{
	//{{{ SaveBackupOptionPane constructor
	public SaveBackupOptionPane()
	{
		super("save-back");
	} //}}}

	//{{{ _init() method
	protected void _init()
	{
		/* Two-stage save */
		twoStageSave = new JCheckBox(jEdit.getProperty(
			"options.save-back.twoStageSave"));
		twoStageSave.setSelected(jEdit.getBooleanProperty(
			"twoStageSave"));
		addComponent(twoStageSave);

		/* Confirm save all */
		confirmSaveAll = new JCheckBox(jEdit.getProperty(
			"options.save-back.confirmSaveAll"));
		confirmSaveAll.setSelected(jEdit.getBooleanProperty(
			"confirmSaveAll"));
		addComponent(confirmSaveAll);

		/* Autosave interval */
		autosave = new JTextField(jEdit.getProperty("autosave"));
		addComponent(jEdit.getProperty("options.save-back.autosave"),autosave);

		/* Backup count */
		backups = new JTextField(jEdit.getProperty("backups"));
		addComponent(jEdit.getProperty("options.save-back.backups"),backups);

		/* Backup directory */
		backupDirectory = new JTextField(jEdit.getProperty(
			"backup.directory"));
		addComponent(jEdit.getProperty("options.save-back.backupDirectory"),
			backupDirectory);

		/* Backup filename prefix */
		backupPrefix = new JTextField(jEdit.getProperty("backup.prefix"));
		addComponent(jEdit.getProperty("options.save-back.backupPrefix"),
			backupPrefix);

		/* Backup suffix */
		backupSuffix = new JTextField(jEdit.getProperty(
			"backup.suffix"));
		addComponent(jEdit.getProperty("options.save-back.backupSuffix"),
			backupSuffix);

		/* Backup on every save */
		backupEverySave = new JCheckBox(jEdit.getProperty(
			"options.save-back.backupEverySave"));
		backupEverySave.setSelected(jEdit.getBooleanProperty("backupEverySave"));
		addComponent(backupEverySave);
	} //}}}

	//{{{ _save() method
	protected void _save()
	{
		jEdit.setBooleanProperty("twoStageSave",twoStageSave.isSelected());
		jEdit.setBooleanProperty("confirmSaveAll",confirmSaveAll.isSelected());
		jEdit.setProperty("autosave",autosave.getText());
		jEdit.setProperty("backups",backups.getText());
		jEdit.setProperty("backup.directory",backupDirectory.getText());
		jEdit.setProperty("backup.prefix",backupPrefix.getText());
		jEdit.setProperty("backup.suffix",backupSuffix.getText());
		jEdit.setBooleanProperty("backupEverySave", backupEverySave.isSelected());
	} //}}}

	//{{{ Private members
	private JCheckBox twoStageSave;
	private JCheckBox confirmSaveAll;
	private JTextField autosave;
	private JTextField backups;
	private JTextField backupDirectory;
	private JTextField backupPrefix;
	private JTextField backupSuffix;
	private JCheckBox backupEverySave;
	//}}}
}
