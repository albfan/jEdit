/*
 * LogViewer.java
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1999, 2004 Slava Pestov
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

//{{{ Imports
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.util.Log;
//}}}

public class LogViewer extends JPanel implements DefaultFocusComponent,
	EBComponent
{
	//{{{ LogViewer constructor
	public LogViewer()
	{
		super(new BorderLayout());

		JPanel caption = new JPanel();
		caption.setLayout(new BoxLayout(caption,BoxLayout.X_AXIS));
		caption.setBorder(new EmptyBorder(6,6,6,6));

		String settingsDirectory = jEdit.getSettingsDirectory();
		if(settingsDirectory != null)
		{
			String[] args = { MiscUtilities.constructPath(
				settingsDirectory, "activity.log") };
			JLabel label = new JLabel(jEdit.getProperty(
				"log-viewer.caption",args));
			caption.add(label);
		}

		caption.add(Box.createHorizontalGlue());

		tailIsOn = jEdit.getBooleanProperty("log-viewer.tail", false);
		tail = new JCheckBox(
			jEdit.getProperty("log-viewer.tail.label"),tailIsOn);
		tail.addActionListener(new ActionHandler());
		caption.add(tail);

		caption.add(Box.createHorizontalStrut(12));

		copy = new JButton(jEdit.getProperty("log-viewer.copy"));
		copy.addActionListener(new ActionHandler());
		caption.add(copy);

		ListModel model = Log.getLogListModel();
		model.addListDataListener(new ListHandler());
		list = new LogList(model);

		add(BorderLayout.NORTH,caption);
		JScrollPane scroller = new JScrollPane(list);
		Dimension dim = scroller.getPreferredSize();
		dim.width = Math.min(600,dim.width);
		scroller.setPreferredSize(dim);
		add(BorderLayout.CENTER,scroller);

		propertiesChanged();
	} //}}}

	//{{{ handleMessage() method
	public void handleMessage(EBMessage msg)
	{
		if(msg instanceof PropertiesChanged)
			propertiesChanged();
	} //}}}

	//{{{ addNotify() method
	public void addNotify()
	{
		super.addNotify();
		if(tailIsOn)
		{
			int index = list.getModel().getSize() - 1;
			list.ensureIndexIsVisible(index);
		}

		EditBus.addToBus(this);
	} //}}}

	//{{{ removeNotify() method
	public void removeNotify()
	{
		super.removeNotify();

		EditBus.removeFromBus(this);
	} //}}}

	//{{{ focusOnDefaultComponent() method
	public void focusOnDefaultComponent()
	{
		list.requestFocus();
	} //}}}

	//{{{ Private members
	private JList list;
	private JButton copy;
	private JCheckBox tail;
	private boolean tailIsOn;

	//{{{ propertiesChanged() method
	private void propertiesChanged()
	{
		list.setFont(jEdit.getFontProperty("view.font"));
		list.setFixedCellHeight(list.getFontMetrics(list.getFont())
			.getHeight());
	} //}}}

	//}}}

	//{{{ ActionHandler class
	class ActionHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			Object src = e.getSource();
			if(src == tail)
			{
				tailIsOn = !tailIsOn;
				jEdit.setBooleanProperty("log-viewer.tail",tailIsOn);
				if(tailIsOn)
				{
					int index = list.getModel().getSize();
					if(index != 0)
					{
						list.ensureIndexIsVisible(index - 1);
					}
				}
			}
			else if(src == copy)
			{
				StringBuffer buf = new StringBuffer();
				Object[] selected = list.getSelectedValues();
				if(selected != null && selected.length != 0)
				{
					for(int i = 0; i < selected.length; i++)
					{
						buf.append(selected[i]);
						buf.append('\n');
					}
				}
				else
				{
					ListModel model = list.getModel();
					for(int i = 0; i < model.getSize(); i++)
					{
						buf.append(model.getElementAt(i));
						buf.append('\n');
					}
				}
				Registers.setRegister('$',buf.toString());
			}
		}
	} //}}}

	//{{{ ListHandler class
	class ListHandler implements ListDataListener
	{
		public void intervalAdded(ListDataEvent e)
		{
			contentsChanged(e);
		}

		public void intervalRemoved(ListDataEvent e)
		{
			contentsChanged(e);
		}

		public void contentsChanged(ListDataEvent e)
		{
			if(tailIsOn)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						int index = list.getModel().getSize() - 1;
						list.ensureIndexIsVisible(index);
					}
				});
			}
		}
	} //}}}

	//{{{ LogList class
	class LogList extends JList
	{
		LogList(ListModel model)
		{
			super(model);
			setVisibleRowCount(24);
			getSelectionModel().setSelectionMode(
				ListSelectionModel.SINGLE_INTERVAL_SELECTION);
			setAutoscrolls(true);
		}

		public void processMouseEvent(MouseEvent evt)
		{
			if(evt.getID() == MouseEvent.MOUSE_PRESSED)
			{
				startIndex = list.locationToIndex(
					evt.getPoint());
			}
			super.processMouseEvent(evt);
		}

		public void processMouseMotionEvent(MouseEvent evt)
		{
			if(evt.getID() == MouseEvent.MOUSE_DRAGGED)
			{
				int row = list.locationToIndex(evt.getPoint());
				if(row != -1)
				{
					if(startIndex == -1)
					{
						list.setSelectionInterval(row,row);
						startIndex = row;
					}
					else
						list.setSelectionInterval(startIndex,row);
					list.ensureIndexIsVisible(row);
					evt.consume();
				}
			}
			else
				super.processMouseMotionEvent(evt);
		}

		private int startIndex;
	} //}}}
}
