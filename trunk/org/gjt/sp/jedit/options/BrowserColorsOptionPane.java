/*
 * BrowserColorsOptionPane.java - Browser colors options panel
 * Copyright (C) 2001 Slava Pestov
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
import org.gjt.sp.jedit.*;

/**
 * Browser color editor.
 * @author Slava Pestov
 * @version $Id$
 */
public class BrowserColorsOptionPane extends AbstractOptionPane
{
	public BrowserColorsOptionPane()
	{
		super("browser.colors");
	}

	// protected members
	protected void _init()
	{
		setLayout(new BorderLayout());

		colorsModel = new BrowserColorsModel();
		colorsTable = new JTable(colorsModel);
		colorsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		colorsTable.getTableHeader().setReorderingAllowed(false);
		colorsTable.addMouseListener(new MouseHandler());
		colorsTable.getSelectionModel().addListSelectionListener(
			new SelectionHandler());
		TableColumnModel tcm = colorsTable.getColumnModel();
		tcm.getColumn(1).setCellRenderer(new BrowserColorsModel.ColorRenderer());
		Dimension d = colorsTable.getPreferredSize();
		d.height = Math.min(d.height,200);
		JScrollPane scroller = new JScrollPane(colorsTable);
		scroller.setPreferredSize(d);
		add(BorderLayout.CENTER,scroller);

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons,BoxLayout.X_AXIS));
		buttons.setBorder(new EmptyBorder(6,0,0,0));

		buttons.add(Box.createGlue());
		add = new JButton(jEdit.getProperty("options.browser.colors.add"));
		add.addActionListener(new ActionHandler());
		buttons.add(add);
		buttons.add(Box.createHorizontalStrut(6));
		remove = new JButton(jEdit.getProperty("options.browser.colors.remove"));
		remove.addActionListener(new ActionHandler());
		buttons.add(remove);
		buttons.add(Box.createGlue());

		add(BorderLayout.SOUTH,buttons);

		updateEnabled();
	}

	protected void _save()
	{
		colorsModel.save();
	}

	// private members
	private BrowserColorsModel colorsModel;
	private JTable colorsTable;
	private JButton add;
	private JButton remove;

	private void updateEnabled()
	{
		int selectedRow = colorsTable.getSelectedRow();
		remove.setEnabled(selectedRow != -1);
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
			Object source = evt.getSource();
			if(source == add)
			{
				colorsModel.add();
			}
			else if(source == remove)
			{
				int selectedRow = colorsTable.getSelectedRow();
				colorsModel.remove(selectedRow);
				updateEnabled();
			}
		}
	}

	class MouseHandler extends MouseAdapter
	{
		public void mouseClicked(MouseEvent evt)
		{
			int row = colorsTable.rowAtPoint(evt.getPoint());
			if(row == -1)
				return;

			Color color = JColorChooser.showDialog(
				BrowserColorsOptionPane.this,
				jEdit.getProperty("colorChooser.title"),
				(Color)colorsModel.getValueAt(row,1));
			if(color != null)
				colorsModel.setValueAt(color,row,1);
		}
	}
}

class BrowserColorsModel extends AbstractTableModel
{
	Vector entries;

	BrowserColorsModel()
	{
		entries = new Vector();

		int i = 0;
		String glob;
		while((glob = jEdit.getProperty("vfs.browser.colors." + i + ".glob")) != null)
		{
			entries.addElement(new Entry(glob,
				jEdit.getColorProperty(
				"vfs.browser.colors." + i + ".color",
				Color.black)));
			i++;
		}
	}

	void add()
	{
		entries.addElement(new Entry(null,null));
		fireTableStructureChanged();
	}

	void remove(int index)
	{
		entries.removeElementAt(index);
		fireTableStructureChanged();
	}

	void save()
	{
		int i;
		for(i = 0; i < entries.size(); i++)
		{
			Entry entry = (Entry)entries.elementAt(i);
			jEdit.setProperty("vfs.browser.colors." + i + ".glob",
				entry.glob);
			jEdit.setColorProperty("vfs.browser.colors." + i + ".color",
				entry.color);
		}
		jEdit.unsetProperty("vfs.browser.colors." + i + ".glob");
		jEdit.unsetProperty("vfs.browser.colors." + i + ".color");
	}

	public int getColumnCount()
	{
		return 2;
	}

	public int getRowCount()
	{
		return entries.size();
	}

	public Object getValueAt(int row, int col)
	{
		Entry entry = (Entry)entries.elementAt(row);

		switch(col)
		{
		case 0:
			return entry.glob;
		case 1:
			return entry.color;
		default:
			return null;
		}
	}

	public boolean isCellEditable(int row, int col)
	{
		return (col == 0);
	}

	public void setValueAt(Object value, int row, int col)
	{
		Entry entry = (Entry)entries.elementAt(row);

		if(col == 0)
			entry.glob = (String)value;
		else
			entry.color = (Color)value;

		fireTableRowsUpdated(row,row);
	}

	public String getColumnName(int index)
	{
		switch(index)
		{
		case 0:
			return jEdit.getProperty("options.browser.colors.glob");
		case 1:
			return jEdit.getProperty("options.browser.colors.color");
		default:
			return null;
		}
	}

	static class Entry
	{
		String glob;
		Color color;

		Entry(String glob, Color color)
		{
			this.glob = glob;
			this.color = color;
		}
	}

	static class ColorRenderer extends JLabel
		implements TableCellRenderer
	{
		public ColorRenderer()
		{
			setOpaque(true);
			setBorder(StyleOptionPane.noFocusBorder);
		}

		// TableCellRenderer implementation
		public Component getTableCellRendererComponent(
			JTable table,
			Object value,
			boolean isSelected,
			boolean cellHasFocus,
			int row,
			int col)
		{
			if (isSelected)
			{
				setBackground(table.getSelectionBackground());
				setForeground(table.getSelectionForeground());
			}
			else
			{
				setBackground(table.getBackground());
				setForeground(table.getForeground());
			}

			if (value != null)
				setBackground((Color)value);

			setBorder((cellHasFocus) ? UIManager.getBorder(
				"Table.focusCellHighlightBorder")
				: StyleOptionPane.noFocusBorder);
			return this;
		}
		// end TableCellRenderer implementation
	}
}
