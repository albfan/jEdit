/*
 * SyntaxHiliteOptionPane.java - Syntax highlighting option pane
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1999, 2000, 2001 Slava Pestov
 * Portions copyright (C) 1999 mike dillon
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
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.Vector;
import org.gjt.sp.jedit.syntax.*;
import org.gjt.sp.jedit.gui.ColorWellButton;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.*;
//}}}

//{{{ SyntaxHiliteOptionPane class
/**
 * Style option pane.
 * @author Slava Pestov
 * @version $Id$
 */
public class SyntaxHiliteOptionPane extends AbstractOptionPane
{
	public static final EmptyBorder noFocusBorder = new EmptyBorder(1,1,1,1);

	//{{{ StyleOptionPane constructor
	public SyntaxHiliteOptionPane()
	{
		super("syntax");
	}
	//}}}

	//{{{ Protected members

	//{{{ _init() method
	protected void _init()
	{
		setLayout(new BorderLayout(6,6));

		add(BorderLayout.CENTER,createStyleTableScroller());
	} //}}}

	//{{{ _save() method
	protected void _save()
	{
		styleModel.save();
	} //}}}

	//}}}

	//{{{ Private members
	private StyleTableModel styleModel;
	private JTable styleTable;

	//{{{ createStyleTableScroller() method
	private JScrollPane createStyleTableScroller()
	{
		styleModel = createStyleTableModel();
		styleTable = new JTable(styleModel);
		styleTable.setRowSelectionAllowed(false);
		styleTable.setColumnSelectionAllowed(false);
		styleTable.setCellSelectionEnabled(false);
		styleTable.getTableHeader().setReorderingAllowed(false);
		styleTable.addMouseListener(new MouseHandler());
		TableColumnModel tcm = styleTable.getColumnModel();
 		TableColumn styleColumn = tcm.getColumn(1);
		styleColumn.setCellRenderer(new StyleTableModel.StyleRenderer());
		Dimension d = styleTable.getPreferredSize();
		d.height = Math.min(d.height,100);
		JScrollPane scroller = new JScrollPane(styleTable);
		scroller.setPreferredSize(d);
		return scroller;
	} //}}}

	//{{{ createStyleTableModel() method
	private StyleTableModel createStyleTableModel()
	{
		return new StyleTableModel();
	} //}}}

	//}}}

	//{{{ MouseHandler class
	class MouseHandler extends MouseAdapter
	{
		public void mouseClicked(MouseEvent evt)
		{
			int row = styleTable.rowAtPoint(evt.getPoint());
			if(row == -1)
				return;

			SyntaxStyle style = new StyleEditor(
				SyntaxHiliteOptionPane.this,
				(SyntaxStyle)styleModel.getValueAt(
				row,1)).getStyle();
			if(style != null)
				styleModel.setValueAt(style,row,1);
		}
	} //}}}
} //}}}

//{{{ StyleTableModel class
class StyleTableModel extends AbstractTableModel
{
	private Vector styleChoices;

	//{{{ StyleTableModel constructor
	StyleTableModel()
	{
		styleChoices = new Vector(Token.ID_COUNT + 4);
		// start at 1 not 0 to skip Token.NULL
		for(int i = 1; i < Token.ID_COUNT; i++)
		{
			String tokenName = Token.tokenToString((byte)i);
			addStyleChoice(tokenName,"view.style." + tokenName.toLowerCase());
		}

		addStyleChoice(jEdit.getProperty("options.syntax.foldLine.1"),
			"view.style.foldLine.1");
		addStyleChoice(jEdit.getProperty("options.syntax.foldLine.2"),
			"view.style.foldLine.2");
		addStyleChoice(jEdit.getProperty("options.syntax.foldLine.3"),
			"view.style.foldLine.3");
		addStyleChoice(jEdit.getProperty("options.syntax.foldLine.0"),
			"view.style.foldLine.0");

		MiscUtilities.quicksort(styleChoices,new MiscUtilities.StringICaseCompare());
	} //}}}

	//{{{ getColumnCount() method
	public int getColumnCount()
	{
		return 2;
	} //}}}

	//{{{ getRowCount() method
	public int getRowCount()
	{
		return styleChoices.size();
	} //}}}

	//{{{ getValueAt() method
	public Object getValueAt(int row, int col)
	{
		StyleChoice ch = (StyleChoice)styleChoices.elementAt(row);
		switch(col)
		{
		case 0:
			return ch.label;
		case 1:
			return ch.style;
		default:
			return null;
		}
	} //}}}

	//{{{ setValueAt() method
	public void setValueAt(Object value, int row, int col)
	{
		StyleChoice ch = (StyleChoice)styleChoices.elementAt(row);
		if(col == 1)
			ch.style = (SyntaxStyle)value;
		fireTableRowsUpdated(row,row);
	} //}}}

	//{{{ getColumnName() method
	public String getColumnName(int index)
	{
		switch(index)
		{
		case 0:
			return jEdit.getProperty("options.syntax.object");
		case 1:
			return jEdit.getProperty("options.syntax.style");
		default:
			return null;
		}
	} //}}}

	//{{{ save() method
	public void save()
	{
		for(int i = 0; i < styleChoices.size(); i++)
		{
			StyleChoice ch = (StyleChoice)styleChoices
				.elementAt(i);
			jEdit.setProperty(ch.property,
				GUIUtilities.getStyleString(ch.style));
		}
	} //}}}

	//{{{ addStyleChoice() method
	private void addStyleChoice(String label, String property)
	{
		styleChoices.addElement(new StyleChoice(label,
			property,
			GUIUtilities.parseStyle(jEdit.getProperty(property),
			"Dialog",12)));
	} //}}}

	//{{{ StyleChoice class
	static class StyleChoice
	{
		String label;
		String property;
		SyntaxStyle style;

		StyleChoice(String label, String property, SyntaxStyle style)
		{
			this.label = label;
			this.property = property;
			this.style = style;
		}

		// for sorting
		public String toString()
		{
			return label;
		}
	} //}}}

	//{{{ StyleRenderer class
	static class StyleRenderer extends JLabel
		implements TableCellRenderer
	{
		//{{{ StyleRenderer constructor
		public StyleRenderer()
		{
			setOpaque(true);
			setBorder(SyntaxHiliteOptionPane.noFocusBorder);
			setText("Hello World");
		} //}}}

		//{{{ getTableCellRendererComponent() method
		public Component getTableCellRendererComponent(
			JTable table,
			Object value,
			boolean isSelected,
			boolean cellHasFocus,
			int row,
			int col)
		{
			if (value != null)
			{
				SyntaxStyle style = (SyntaxStyle)value;
				setForeground(style.getForegroundColor());
				if (style.getBackgroundColor() != null) 
					setBackground(style.getBackgroundColor());
				else
				{
					// this part sucks
					setBackground(jEdit.getColorProperty(
						"view.bgColor"));
				}
				setFont(style.getFont());
			}

			setBorder((cellHasFocus) ? UIManager.getBorder(
				"Table.focusCellHighlightBorder")
				: SyntaxHiliteOptionPane.noFocusBorder);
			return this;
		} //}}}
	} //}}}
} //}}}

//{{{ StyleEditor class
class StyleEditor extends EnhancedDialog implements ActionListener
{
	//{{{ StyleEditor constructor
	StyleEditor(Component comp, SyntaxStyle style)
	{
		super(GUIUtilities.getParentDialog(comp),
			jEdit.getProperty("style-editor.title"),true);

		JPanel content = new JPanel(new BorderLayout(12,12));
		content.setBorder(new EmptyBorder(12,12,12,12));
		setContentPane(content);

		JPanel panel = new JPanel(new GridLayout(4,2,12,12));

		italics = new JCheckBox(jEdit.getProperty("style-editor.italics"));
		italics.setSelected(style.getFont().isItalic());
		panel.add(italics);
		panel.add(new JLabel());

		bold = new JCheckBox(jEdit.getProperty("style-editor.bold"));
		bold.setSelected(style.getFont().isBold());
		panel.add(bold);
		panel.add(new JLabel());

		Color fg = style.getForegroundColor();

		fgColorCheckBox = new JCheckBox(jEdit.getProperty("style-editor.fgColor"));
		fgColorCheckBox.setSelected(fg != null);
		fgColorCheckBox.addActionListener(this);
		panel.add(fgColorCheckBox);

		fgColor = new ColorWellButton(fg);
		fgColor.setEnabled(fg != null);
		panel.add(fgColor);

		Color bg = style.getBackgroundColor();
		bgColorCheckBox = new JCheckBox(jEdit.getProperty("style-editor.bgColor"));
		bgColorCheckBox.setSelected(bg != null);
		bgColorCheckBox.addActionListener(this);
		panel.add(bgColorCheckBox);

		bgColor = new ColorWellButton(bg);
		bgColor.setEnabled(bg != null);
		panel.add(bgColor);

		content.add(BorderLayout.CENTER,panel);

		Box box = new Box(BoxLayout.X_AXIS);
		box.add(Box.createGlue());
		box.add(ok = new JButton(jEdit.getProperty("common.ok")));
		getRootPane().setDefaultButton(ok);
		ok.addActionListener(this);
		box.add(Box.createHorizontalStrut(6));
		box.add(cancel = new JButton(jEdit.getProperty("common.cancel")));
		cancel.addActionListener(this);
		box.add(Box.createGlue());

		content.add(BorderLayout.SOUTH,box);

		pack();
		setLocationRelativeTo(GUIUtilities.getParentDialog(comp));

		setResizable(false);
		setVisible(true);
	} //}}}

	//{{{ actionPerformed() method
	public void actionPerformed(ActionEvent evt)
	{
		Object source = evt.getSource();
		if(source == ok)
			ok();
		else if(source == cancel)
			cancel();
		else if(source == fgColorCheckBox)
			fgColor.setEnabled(fgColorCheckBox.isSelected());
		else if(source == bgColorCheckBox)
			bgColor.setEnabled(bgColorCheckBox.isSelected());
	} //}}}

	//{{{ ok() method
	public void ok()
	{
		okClicked = true;
		dispose();
	} //}}}

	//{{{ cancel() method
	public void cancel()
	{
		dispose();
	} //}}}

	//{{{ getStyle() method
	public SyntaxStyle getStyle()
	{
		if(!okClicked)
			return null;

		Color foreground = (fgColorCheckBox.isSelected()
			? fgColor.getSelectedColor()
			: null);

		Color background = (bgColorCheckBox.isSelected()
			? bgColor.getSelectedColor()
			: null);

		return new SyntaxStyle(foreground,background,
				new Font("Dialog",
				(italics.isSelected() ? Font.ITALIC : 0)
				| (bold.isSelected() ? Font.BOLD : 0),
				12));
	} //}}}

	//{{{ Private members
	private JCheckBox italics;
	private JCheckBox bold;
	private JCheckBox fgColorCheckBox;
	private ColorWellButton fgColor;
	private JCheckBox bgColorCheckBox;
	private ColorWellButton bgColor;
	private JButton ok;
	private JButton cancel;
	private boolean okClicked;
	//}}}
} //}}}
