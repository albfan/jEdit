/*
 * AbbrevsOptionPane.java - Abbrevs options panel
 * Copyright (C) 1999, 2000, 2001 Slava Pestov
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

import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;
import org.gjt.sp.jedit.gui.EditAbbrevDialog;
import org.gjt.sp.jedit.*;

/**
 * Abbrev editor.
 * @author Slava Pestov
 * @version $Id$
 */
public class AbbrevsOptionPane extends AbstractOptionPane
{
	public AbbrevsOptionPane()
	{
		super("abbrevs");
	}

	// protected members
	protected void _init()
	{
		setLayout(new BorderLayout());

		JPanel panel = new JPanel(new BorderLayout());

		JPanel panel2 = new JPanel();
		panel2.setLayout(new BoxLayout(panel2,BoxLayout.X_AXIS));
		panel2.setBorder(new EmptyBorder(0,0,6,0));

		panel2.add(Box.createGlue());

		expandOnInput = new JCheckBox(jEdit.getProperty("options.abbrevs"
			+ ".expandOnInput"),Abbrevs.getExpandOnInput());
		panel2.add(expandOnInput);

		panel2.add(Box.createGlue());

		panel.add(panel2,BorderLayout.NORTH);

		JPanel panel3 = new JPanel();
		panel3.setLayout(new BoxLayout(panel3,BoxLayout.X_AXIS));
		panel3.setBorder(new EmptyBorder(0,0,6,0));
		panel3.add(Box.createGlue());
		JLabel label = new JLabel(jEdit.getProperty("options.abbrevs.set"));
		label.setBorder(new EmptyBorder(0,0,0,12));
		panel3.add(label);

		Hashtable _modeAbbrevs = Abbrevs.getModeAbbrevs();
		modeAbbrevs = new Hashtable();
		Mode[] modes = jEdit.getModes();
		String[] sets = new String[modes.length + 1];
		sets[0] = "global";
		for(int i = 0; i < modes.length; i++)
		{
			String name = modes[i].getName();
			sets[i+1] = name;
			modeAbbrevs.put(name,new AbbrevsModel((Hashtable)_modeAbbrevs.get(name)));
		}

		setsComboBox = new JComboBox(sets);
		ActionHandler actionHandler = new ActionHandler();
		setsComboBox.addActionListener(actionHandler);
		panel3.add(setsComboBox);
		panel3.add(Box.createGlue());
		panel.add(panel3,BorderLayout.SOUTH);

		add(BorderLayout.NORTH,panel);

		globalAbbrevs = new AbbrevsModel(Abbrevs.getGlobalAbbrevs());
		abbrevsTable = new JTable(globalAbbrevs);
		abbrevsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		abbrevsTable.getTableHeader().setReorderingAllowed(false);
		abbrevsTable.getTableHeader().addMouseListener(new HeaderMouseHandler());
		abbrevsTable.getSelectionModel().addListSelectionListener(
			new SelectionHandler());
		Dimension d = abbrevsTable.getPreferredSize();
		d.height = Math.min(d.height,200);
		JScrollPane scroller = new JScrollPane(abbrevsTable);
		scroller.setPreferredSize(d);
		add(BorderLayout.CENTER,scroller);

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons,BoxLayout.X_AXIS));
		buttons.setBorder(new EmptyBorder(6,0,0,0));

		buttons.add(Box.createGlue());
		add = new JButton(jEdit.getProperty("options.abbrevs.add"));
		add.addActionListener(actionHandler);
		buttons.add(add);
		buttons.add(Box.createHorizontalStrut(6));
		edit = new JButton(jEdit.getProperty("options.abbrevs.edit"));
		edit.addActionListener(actionHandler);
		buttons.add(edit);
		buttons.add(Box.createHorizontalStrut(6));
		remove = new JButton(jEdit.getProperty("options.abbrevs.remove"));
		remove.addActionListener(actionHandler);
		buttons.add(remove);
		buttons.add(Box.createGlue());

		add(BorderLayout.SOUTH,buttons);

		updateEnabled();
	}

	protected void _save()
	{
		if(abbrevsTable.getCellEditor() != null)
			abbrevsTable.getCellEditor().stopCellEditing();

		Abbrevs.setExpandOnInput(expandOnInput.isSelected());

		Abbrevs.setGlobalAbbrevs(globalAbbrevs.toHashtable());

		Hashtable modeHash = new Hashtable();
		Enumeration keys = modeAbbrevs.keys();
		Enumeration values = modeAbbrevs.elements();
		while(keys.hasMoreElements())
		{
			modeHash.put(keys.nextElement(),((AbbrevsModel)values.nextElement())
				.toHashtable());
		}
		Abbrevs.setModeAbbrevs(modeHash);
	}

	// private members
	private JComboBox setsComboBox;
	private JCheckBox expandOnInput;
	private JTable abbrevsTable;
	private AbbrevsModel globalAbbrevs;
	private Hashtable modeAbbrevs;
	private JButton add;
	private JButton edit;
	private JButton remove;

	private void updateEnabled()
	{
		int selectedRow = abbrevsTable.getSelectedRow();
		edit.setEnabled(selectedRow != -1);
		remove.setEnabled(selectedRow != -1);
	}

	class HeaderMouseHandler extends MouseAdapter
	{
		public void mouseClicked(MouseEvent evt)
		{
			switch(abbrevsTable.getTableHeader().columnAtPoint(evt.getPoint()))
			{
			case 0:
				((AbbrevsModel)abbrevsTable.getModel()).sort(0);
				break;
			case 1:
				((AbbrevsModel)abbrevsTable.getModel()).sort(1);
				break;
			}
		}
	}

	class SelectionHandler implements ListSelectionListener
	{
		public void valueChanged(ListSelectionEvent evt)
		{
			updateEnabled();
		}
	}

	class ActionHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent evt)
		{
			AbbrevsModel abbrevsModel = (AbbrevsModel)abbrevsTable.getModel();

			Object source = evt.getSource();
			if(source == setsComboBox)
			{
				String selected = (String)setsComboBox.getSelectedItem();
				if(selected.equals("global"))
				{
					abbrevsTable.setModel(globalAbbrevs);
				}
				else
				{
					abbrevsTable.setModel((AbbrevsModel)
						modeAbbrevs.get(selected));
				}
				updateEnabled();
			}
			else if(source == add)
			{
				EditAbbrevDialog dialog = new EditAbbrevDialog(
					AbbrevsOptionPane.this,
					null,null);
				String abbrev = dialog.getAbbrev();
				String expansion = dialog.getExpansion();
				if(abbrev != null && abbrev.length() != 0
					&& expansion != null
					&& expansion.length() != 0)
				{
					abbrevsModel.add(abbrev,expansion);
					int index = abbrevsModel.getRowCount() - 1;
					abbrevsTable.getSelectionModel()
						.setSelectionInterval(index,index);
					Rectangle rect = abbrevsTable.getCellRect(
						index,0,true);
					abbrevsTable.scrollRectToVisible(rect);
					updateEnabled();
				}
			}
			else if(source == edit)
			{
				int row = abbrevsTable.getSelectedRow();

				String abbrev = (String)abbrevsModel.getValueAt(row,0);
				String expansion = (String)abbrevsModel.getValueAt(row,1);

				EditAbbrevDialog dialog = new EditAbbrevDialog(
					AbbrevsOptionPane.this,
					abbrev,expansion);
				abbrev = dialog.getAbbrev();
				expansion = dialog.getExpansion();
				if(abbrev != null && expansion != null)
				{
					abbrevsModel.setValueAt(abbrev,row,0);
					abbrevsModel.setValueAt(expansion,row,1);
				}
			}
			else if(source == remove)
			{
				int selectedRow = abbrevsTable.getSelectedRow();
				abbrevsModel.remove(selectedRow);
				updateEnabled();
			}
		}
	}
}

class AbbrevsModel extends AbstractTableModel
{
	Vector abbrevs;

	AbbrevsModel()
	{
		abbrevs = new Vector();
	}

	AbbrevsModel(Hashtable abbrevHash)
	{
		this();

		if(abbrevHash != null)
		{
			Enumeration abbrevEnum = abbrevHash.keys();
			Enumeration expandEnum = abbrevHash.elements();

			while(abbrevEnum.hasMoreElements())
			{
				abbrevs.addElement(new Abbrev((String)abbrevEnum.nextElement(),
					(String)expandEnum.nextElement()));
			}

			sort(0);
		}
	}

	void sort(int col)
	{
		MiscUtilities.quicksort(abbrevs,new AbbrevCompare(col));
		fireTableDataChanged();
	}

	void add(String abbrev, String expansion)
	{
		abbrevs.addElement(new Abbrev(abbrev,expansion));
		fireTableStructureChanged();
	}

	void remove(int index)
	{
		abbrevs.removeElementAt(index);
		fireTableStructureChanged();
	}

	public Hashtable toHashtable()
	{
		Hashtable hash = new Hashtable();
		for(int i = 0; i < abbrevs.size(); i++)
		{
			Abbrev abbrev = (Abbrev)abbrevs.elementAt(i);
			if(abbrev.abbrev.length() > 0
				&& abbrev.expand.length() > 0)
			{
				hash.put(abbrev.abbrev,abbrev.expand);
			}
		}
		return hash;
	}

	public int getColumnCount()
	{
		return 2;
	}

	public int getRowCount()
	{
		return abbrevs.size();
	}

	public Object getValueAt(int row, int col)
	{
		Abbrev abbrev = (Abbrev)abbrevs.elementAt(row);
		switch(col)
		{
		case 0:
			return abbrev.abbrev;
		case 1:
			return abbrev.expand;
		default:
			return null;
		}
	}

	public boolean isCellEditable(int row, int col)
	{
		return false;
	}

	public void setValueAt(Object value, int row, int col)
	{
		if(value == null)
			value = "";

		Abbrev abbrev = (Abbrev)abbrevs.elementAt(row);

		if(col == 0)
			abbrev.abbrev = (String)value;
		else
			abbrev.expand = (String)value;

		fireTableRowsUpdated(row,row);
	}

	public String getColumnName(int index)
	{
		switch(index)
		{
		case 0:
			return jEdit.getProperty("options.abbrevs.abbrev");
		case 1:
			return jEdit.getProperty("options.abbrevs.expand");
		default:
			return null;
		}
	}

	class AbbrevCompare implements MiscUtilities.Compare
	{
		int col;

		AbbrevCompare(int col)
		{
			this.col = col;
		}

		public int compare(Object obj1, Object obj2)
		{
			Abbrev a1 = (Abbrev)obj1;
			Abbrev a2 = (Abbrev)obj2;

			if(col == 0)
			{
				String abbrev1 = a1.abbrev.toLowerCase();
				String abbrev2 = a2.abbrev.toLowerCase();

				return abbrev1.compareTo(abbrev2);
			}
			else
			{
				String expand1 = a1.expand.toLowerCase();
				String expand2 = a2.expand.toLowerCase();

				return expand1.compareTo(expand2);
			}
		}
	}
}

class Abbrev
{
	Abbrev() {}

	Abbrev(String abbrev, String expand)
	{
		this.abbrev = abbrev;
		this.expand = expand;
	}

	String abbrev;
	String expand;
}
