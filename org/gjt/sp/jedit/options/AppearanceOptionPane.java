/*
 * AppearanceOptionPane.java - Appearance options panel
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2001, 2004 Slava Pestov
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
import java.awt.*;
import java.io.*;
import org.gjt.sp.jedit.gui.FontSelector;
import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;
//}}}

public class AppearanceOptionPane extends AbstractOptionPane
{
	//{{{ AppearanceOptionPane constructor
	public AppearanceOptionPane()
	{
		super("appearance");
	} //}}}

	//{{{ _init() method
	protected void _init()
	{
		/* Look and feel */
		addComponent(new JLabel(jEdit.getProperty("options.appearance.lf.note")));

		lfs = UIManager.getInstalledLookAndFeels();
		String[] names = new String[lfs.length];
		String lf = UIManager.getLookAndFeel().getClass().getName();
		int index = 0;
		for(int i = 0; i < names.length; i++)
		{
			names[i] = lfs[i].getName();
			if(lf.equals(lfs[i].getClassName()))
				index = i;
		}

		lookAndFeel = new JComboBox(names);
		lookAndFeel.setSelectedIndex(index);
		lookAndFeel.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt)
			{
				updateEnabled();
			}
		});

		addComponent(jEdit.getProperty("options.appearance.lf"),
			lookAndFeel);

		/* Primary Metal L&F font */
		primaryFont = new FontSelector(jEdit.getFontProperty(
			"metal.primary.font"));
		addComponent(jEdit.getProperty("options.appearance.primaryFont"),
			primaryFont);

		/* Secondary Metal L&F font */
		secondaryFont = new FontSelector(jEdit.getFontProperty(
			"metal.secondary.font"));
		addComponent(jEdit.getProperty("options.appearance.secondaryFont"),
			secondaryFont);

		updateEnabled();

		/* History count */
		history = new JTextField(jEdit.getProperty("history"));
		addComponent(jEdit.getProperty("options.appearance.history"),history);

		/* Menu spillover count */
		menuSpillover = new JTextField(jEdit.getProperty("menu.spillover"));
		addComponent(jEdit.getProperty("options.appearance.menuSpillover"),menuSpillover);

		addSeparator("options.appearance.startup.label");

		/* Show splash screen */
		showSplash = new JCheckBox(jEdit.getProperty(
			"options.appearance.showSplash"));
		String settingsDirectory = jEdit.getSettingsDirectory();
		if(settingsDirectory == null)
			showSplash.setSelected(true);
		else
			showSplash.setSelected(!new File(settingsDirectory,"nosplash").exists());
		addComponent(showSplash);

		/* Show tip of the day */
		showTips = new JCheckBox(jEdit.getProperty(
			"options.appearance.showTips"));
		showTips.setSelected(jEdit.getBooleanProperty("tip.show"));
		addComponent(showTips);

		addSeparator("options.appearance.experimental.label");
		addComponent(GUIUtilities.createMultilineLabel(
			jEdit.getProperty("options.appearance.experimental.caption")));

		/* Use jEdit colors in all text components */
		textColors = new JCheckBox(jEdit.getProperty(
			"options.appearance.textColors"));
		textColors.setSelected(jEdit.getBooleanProperty("textColors"));
		addComponent(textColors);

		/* Decorate frames with look and feel (JDK 1.4 only) */
		decorateFrames = new JCheckBox(jEdit.getProperty(
			"options.appearance.decorateFrames"));
		decorateFrames.setSelected(jEdit.getBooleanProperty("decorate.frames"));
		addComponent(decorateFrames);

		/* Decorate dialogs with look and feel (JDK 1.4 only) */
		decorateDialogs = new JCheckBox(jEdit.getProperty(
			"options.appearance.decorateDialogs"));
		decorateDialogs.setSelected(jEdit.getBooleanProperty("decorate.dialogs"));
		addComponent(decorateDialogs);
	} //}}}

	//{{{ _save() method
	protected void _save()
	{
		String lf = lfs[lookAndFeel.getSelectedIndex()].getClassName();
		jEdit.setProperty("lookAndFeel",lf);
		jEdit.setFontProperty("metal.primary.font",primaryFont.getFont());
		jEdit.setFontProperty("metal.secondary.font",secondaryFont.getFont());
		jEdit.setProperty("history",history.getText());
		jEdit.setProperty("menu.spillover",menuSpillover.getText());
		jEdit.setBooleanProperty("tip.show",showTips.isSelected());

		// this is handled a little differently from other jEdit settings
		// as the splash screen flag needs to be known very early in the
		// startup sequence, before the user properties have been loaded
		String settingsDirectory = jEdit.getSettingsDirectory();
		if(settingsDirectory != null)
		{
			File file = new File(settingsDirectory,"nosplash");
			if(showSplash.isSelected())
				file.delete();
			else
			{
				FileOutputStream out = null;
				try
				{
					out = new FileOutputStream(file);
					out.write('\n');
					out.close();
				}
				catch(IOException io)
				{
					Log.log(Log.ERROR,this,io);
				}
				finally
				{
					try
					{
						if(out != null)
							out.close();
					}
					catch(IOException e)
					{
					}
				}
			}
		}
		jEdit.setBooleanProperty("textColors",textColors.isSelected());
		jEdit.setBooleanProperty("decorate.frames",decorateFrames.isSelected());
		jEdit.setBooleanProperty("decorate.dialogs",decorateDialogs.isSelected());
	} //}}}

	//{{{ Private members

	//{{{ Instance variables
	private UIManager.LookAndFeelInfo[] lfs;
	private JComboBox lookAndFeel;
	private FontSelector primaryFont;
	private FontSelector secondaryFont;
	private JTextField history;
	private JTextField menuSpillover;
	private JCheckBox showTips;
	private JCheckBox showSplash;
	private JCheckBox textColors;
	private JCheckBox decorateFrames;
	private JCheckBox decorateDialogs;
	//}}}

	//{{{ updateEnabled() method
	private void updateEnabled()
	{
		String className = lfs[lookAndFeel.getSelectedIndex()]
			.getClassName();

		if(className.equals("javax.swing.plaf.metal.MetalLookAndFeel")
			|| className.equals("com.incors.plaf.kunststoff.KunststoffLookAndFeel"))
		{
			primaryFont.setEnabled(true);
			secondaryFont.setEnabled(true);
		}
		else
		{
			primaryFont.setEnabled(false);
			secondaryFont.setEnabled(false);
		}
	} //}}}

	//}}}
}
