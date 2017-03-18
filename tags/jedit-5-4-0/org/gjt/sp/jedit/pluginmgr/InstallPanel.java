/*
 * InstallPanel.java - For installing plugins
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2002-2013 Kris Kopicki, Slava Pestov, Dale Anson,
 *      Matthieu Casanova, Alan Ezust, Björn "Vampire" Kautler
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

package org.gjt.sp.jedit.pluginmgr;

//{{{ Imports
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.io.VFS;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.util.GenericGUIUtilities;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.StandardUtilities;
import org.gjt.sp.util.StringList;
import org.gjt.sp.util.XMLUtilities;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.InputStream;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
//}}}

/**
 * @version $Id$
 */
class InstallPanel extends JPanel implements EBComponent
{

	//{{{ Variables
	private final JTable table;
	private final JScrollPane scrollpane;
	private final PluginTableModel pluginModel;
	private final PluginManager window;
	private final PluginInfoBox infoBox;
	private final ChoosePluginSet chooseButton;
	private final boolean updates;
	private final CardLayout layout;
	private final JTextField searchField;
	private boolean hideInstalled;
	private boolean isLoading;

	private final Collection<String> pluginSet = new HashSet<String>();
	//}}}

	//{{{ InstallPanel constructor
	InstallPanel(PluginManager window, boolean updates)
	{
		super(null);
		setLayout(layout = new CardLayout());

		this.window = window;
		this.updates = updates;

		setBorder(new EmptyBorder(12,12,12,12));

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split.setResizeWeight(0.75);
		/* Setup the table */
		table = new JTable(pluginModel = new PluginTableModel());
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0,0));
		table.setRowHeight(GenericGUIUtilities.defaultRowHeight() + 2);
		table.setPreferredScrollableViewportSize(new Dimension(500,200));
		table.setDefaultRenderer(Object.class, new TextRenderer(
			(DefaultTableCellRenderer)table.getDefaultRenderer(Object.class)));
		table.addFocusListener(new TableFocusHandler());
		InputMap tableInputMap = table.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap tableActionMap = table.getActionMap();
		tableInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,0),"tabOutForward");
		tableActionMap.put("tabOutForward",new KeyboardAction(KeyboardCommand.TAB_OUT_FORWARD));
		tableInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,InputEvent.SHIFT_MASK),"tabOutBack");
		tableActionMap.put("tabOutBack",new KeyboardAction(KeyboardCommand.TAB_OUT_BACK));
		tableInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"editPlugin");
		tableActionMap.put("editPlugin",new KeyboardAction(KeyboardCommand.EDIT_PLUGIN));
		tableInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"closePluginManager");
		tableActionMap.put("closePluginManager",new KeyboardAction(KeyboardCommand.CLOSE_PLUGIN_MANAGER));

		TableColumn col1 = table.getColumnModel().getColumn(0);
		TableColumn col2 = table.getColumnModel().getColumn(1);
		TableColumn col3 = table.getColumnModel().getColumn(2);
		TableColumn col4 = table.getColumnModel().getColumn(3);
		TableColumn col5 = table.getColumnModel().getColumn(4);

		col1.setPreferredWidth(30);
		col1.setMinWidth(30);
		col1.setMaxWidth(30);
		col1.setResizable(false);

		col2.setPreferredWidth(180);
		col3.setPreferredWidth(130);
		col4.setPreferredWidth(70);
		col5.setPreferredWidth(70);

		JTableHeader header = table.getTableHeader();
		header.setReorderingAllowed(false);
		header.addMouseListener(new HeaderMouseHandler());
		header.setDefaultRenderer(new HeaderRenderer(
			(DefaultTableCellRenderer)header.getDefaultRenderer()));

		scrollpane = new JScrollPane(table);
		scrollpane.getViewport().setBackground(table.getBackground());
		split.setTopComponent(scrollpane);

		/* Create description */
		JScrollPane infoPane = new JScrollPane(
			infoBox = new PluginInfoBox());
		infoPane.setPreferredSize(new Dimension(500,100));
		split.setBottomComponent(infoPane);

		EventQueue.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				split.setDividerLocation(0.75);
			}
		});

		searchField = new JTextField();
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)
				{
					table.dispatchEvent(e);
					table.requestFocus();
				}
			}
		});
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			void update()
			{
				pluginModel.setFilterString(searchField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				update();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				update();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				update();
			}
		});
		table.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				int i = table.getSelectedRow(), n = table.getModel().getRowCount();
				if (e.getKeyChar() == '/' ||
					e.getKeyCode() == KeyEvent.VK_DOWN && i == (n - 1) ||
					e.getKeyCode() == KeyEvent.VK_UP && i == 0)
				{
					searchField.requestFocus();
					searchField.selectAll();
				}
			}
			@Override
			public void keyTyped(KeyEvent e)
			{
				char c = e.getKeyChar();
				if(c != KeyEvent.CHAR_UNDEFINED && Character.isAlphabetic(c))
				{
					searchField.dispatchEvent(e);
					searchField.requestFocus();
				}
			}
		});
		hideInstalled = !updates;
		final JCheckBox hideInstalledCB = new JCheckBox("Hide installed plugins", hideInstalled);
		hideInstalledCB.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				InstallPanel.this.hideInstalled = hideInstalledCB.isSelected();
				updateModel();
			}
		});
		hideInstalledCB.setAlignmentX(0);

		Box filterBox = Box.createHorizontalBox();
		filterBox.add(new JLabel("Filter : "));
		filterBox.add(searchField);
		filterBox.setAlignmentX(0);
		Box topBox = new Box(BoxLayout.PAGE_AXIS);
		topBox.add(filterBox);
		if(!updates){
			topBox.add(hideInstalledCB);
		}

		/* Create buttons */
		Box buttons = new Box(BoxLayout.X_AXIS);

		buttons.add(new InstallButton());
		buttons.add(Box.createHorizontalStrut(12));
		buttons.add(new SelectallButton());
		buttons.add(chooseButton = new ChoosePluginSet());
		buttons.add(new ClearPluginSet());
		buttons.add(Box.createGlue());
		buttons.add(new SizeLabel());

		JPanel _installPanel = new JPanel(new BorderLayout(12, 12));
		_installPanel.add(BorderLayout.NORTH,topBox);
		_installPanel.add(BorderLayout.CENTER,split);
		_installPanel.add(BorderLayout.SOUTH, buttons);

		add(_installPanel, "INSTALL");

		JPanel loadingLabelPanel = new JPanel(new GridBagLayout());
		loadingLabelPanel.add(new JLabel("<html><b><strong>" + jEdit.getProperty("common.loading", "Loading...") +
								  "</strong></b></html>"));
		add(loadingLabelPanel, "LOADING");

		JPanel noAvailablePluginsPanel = new JPanel(new GridBagLayout());
		noAvailablePluginsPanel.add(new JLabel("<html><b><strong>" +
			jEdit.getProperty("options.plugin-manager.no-plugin-available", "No available plugins...") +
			"</strong></b></html>"));
		add(noAvailablePluginsPanel, "NO_PLUGIN_AVAILABLE");

		JPanel pluginsAreUpToDatePanel = new JPanel(new GridBagLayout());
		pluginsAreUpToDatePanel.add(new JLabel("<html><b><strong>" +
			jEdit.getProperty("options.plugin-manager.no-plugin-uptodate", "Plugins are up to date...") +
			"</strong></b></html>"));
		add(pluginsAreUpToDatePanel, "PLUGIN_ARE_UP_TO_DATE");

		layout.show(this, "INSTALL");
		String path = jEdit.getProperty(PluginManager.PROPERTY_PLUGINSET, "");
		if (!path.isEmpty())
		{
			loadPluginSet(path);
		}
	} //}}}

	//{{{ loadPluginSet() method
	/** loads a pluginSet xml file and updates the model to reflect
	    certain checked selections
	    @since jEdit 4.3pre10
	    @author Alan Ezust
	*/
	boolean loadPluginSet(String path)
	{
		pluginSet.clear();
		pluginModel.clearSelection();

		VFS vfs = VFSManager.getVFSForPath(path);
		Object session = vfs.createVFSSession(path, InstallPanel.this);
		try
		{
			InputStream is = vfs._createInputStream(session, path, false, InstallPanel.this);
			XMLUtilities.parseXML(is, new StringMapHandler());
		}
		catch (Exception e)
		{
			Log.log(Log.WARNING, this, "Loading Pluginset failed:" + e.getMessage());
			return false;
		}
		pluginModel.update();
		return true;
	} //}}}

	//{{{ updateModel() method
	/**
	 * Must be called from the EDT.
	 **/
	public void updateModel()
	{
		infoBox.setText(null);
		pluginModel.update();
		if (pluginModel.getRowCount() == 0)
		{
			if (updates)
				layout.show(InstallPanel.this, "PLUGIN_ARE_UP_TO_DATE");
			else
				layout.show(InstallPanel.this, "NO_PLUGIN_AVAILABLE");
		}
		else
		{
			layout.show(InstallPanel.this, "INSTALL");
			EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					searchField.requestFocusInWindow();
				}
			});
		}
		isLoading = false;
	} //}}}

	//{{{ handleMessage() method
	@Override
	public void handleMessage(EBMessage message)
	{
		 if (message.getSource() == PluginManager.getInstance())
		 {
			 chooseButton.path = jEdit.getProperty(PluginManager.PROPERTY_PLUGINSET, "");
			 if (chooseButton.path.length() > 0)
			 {
				 loadPluginSet(chooseButton.path);
				 pluginModel.clearSelection();
				 chooseButton.updateUI();
			 }
		}
	} //}}}

	//{{{ loading() method
	void loading()
	{
		layout.show(this, "LOADING");
		isLoading = true;
	} //}}}

	//{{{ Private members

	//{{{ isDownloadingList() method
	private boolean isDownloadingList()
	{
		return isLoading;
	} //}}}


	//{{{ formatSize() method
	private static String formatSize(int size)
	{
		NumberFormat df = NumberFormat.getInstance();
		df.setMaximumFractionDigits(1);
		df.setMinimumFractionDigits(0);
		String sizeText;
		if (size < 1048576)
			sizeText = (size >> 10) + "KB";
		else
			sizeText = df.format(size/ 1048576.0d) + "MB";
		return sizeText;
	} //}}}

	//}}}

	//{{{ Inner classes

	//{{{ PluginTableModel class
	private class PluginTableModel extends AbstractTableModel
	{
		private final List<Entry> entries = new ArrayList<Entry>();
		private final List<Entry> filteredEntries = new ArrayList<Entry>();
		private int sortType = EntryCompare.COLUMN_NAME;
		private String filterString;
		int sortDirection = 1;

		//{{{ setFilterString() method
		public void setFilterString(String filterString)
		{
			this.filterString = filterString;
			updateFilteredEntries();
		} //}}}

		//{{{ updateFilteredEntries() method
		void updateFilteredEntries()
		{
			filteredEntries.clear();
			if (filterString == null)
			{
				if(!hideInstalled)
				{
					filteredEntries.addAll(entries);
				}
				else
				{
					for(Entry e: entries)
					{
						if(e.install || e.installedVersion == null || updates)
						{
							filteredEntries.add(e);
						}
					}
				}
			}
			else
			{
				String[] words = filterString.toLowerCase().split("\\s+");
				for (Entry e : entries)
				{
					if (e.install)
					{
						filteredEntries.add(e);
						continue;
					}

					if(hideInstalled && e.installedVersion != null)
					{
						continue;
					}

					String s = (e.name + ' ' + e.set + ' ' + e.description).toLowerCase();
					boolean hasAll = true;
					boolean negative = false;
					for (String word : words)
					{
						if (word.startsWith("-"))
						{
							word = word.substring(1);
							negative = true;
						}
						if (word.length() == 0)
							continue;

						if (negative == s.contains(word))
						{
							hasAll = false;
							break;
						}
						negative = false;
					}
					if (hasAll)
						filteredEntries.add(e);
				}
			}
			fireTableChanged(new TableModelEvent(PluginTableModel.this));
		} //}}}

		//{{{ getColumnClass() method
		@Override
		public Class getColumnClass(int columnIndex)
		{
			switch (columnIndex)
			{
				case 0: return Boolean.class;
				case 1:
				case 2:
				case 3:
				case 4:
				case 5: return Object.class;
				default: throw new Error("Column out of range");
			}
		} //}}}

		//{{{ getColumnCount() method
		@Override
		public int getColumnCount()
		{
			return 6;
		} //}}}

		//{{{ getColumnName() method
		@Override
		public String getColumnName(int column)
		{
			switch (column)
			{
				case 0: return " ";
				case 1: return ' '+jEdit.getProperty("install-plugins.info.name");
				case 2: return ' '+jEdit.getProperty("install-plugins.info.category");
				case 3: return ' '+jEdit.getProperty("install-plugins.info.version");
				case 4: return ' '+jEdit.getProperty("install-plugins.info.size");
				case 5: return ' '+jEdit.getProperty("install-plugins.info.releaseDate");
				default: throw new Error("Column out of range");
			}
		} //}}}

		//{{{ getRowCount() method
		@Override
		public int getRowCount()
		{
			return filteredEntries.size();
		} //}}}

		//{{{ getValueAt() method
		@Override
		public Object getValueAt(int rowIndex,int columnIndex)
		{
			Object obj = filteredEntries.get(rowIndex);
			if(obj instanceof String)
			{
				if(columnIndex == 1)
					return obj;
				else
					return null;
			}
			else
			{
				Entry entry = (Entry)obj;

				switch (columnIndex)
				{
					case 0:
						return entry.install;
					case 1:
						return entry.name;
					case 2:
						return entry.set;
					case 3:
						if (entry.installedVersion != null)
							return entry.installedVersion + "->" + entry.version;
						return entry.version;
					case 4:
						return formatSize(entry.size);
					case 5:
						return entry.date;
					default:
						throw new Error("Column out of range");
				}
			}
		} //}}}

		//{{{ isCellEditable() method
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex)
		{
			return columnIndex == 0;
		} //}}}

		//{{{ setSelectAll() method
		public void setSelectAll(boolean b)
		{
			if(isDownloadingList())
				return;

			int length = getRowCount();
			for (int i = 0; i < length; i++)
			{
				if (b)
					setValueAt(Boolean.TRUE,i,0);
				else
				{
					Entry entry = (Entry)filteredEntries.get(i);
					entry.dependents = new LinkedList<Entry>();
					entry.install = false;
				}
			}
			fireTableChanged(new TableModelEvent(this));
		} //}}}

		//{{{ setSortType() method
		public void setSortType(int type)
		{
			sortType = type;
			sort(type);
		} //}}}

		//{{{ deselectDependents() method
		/**
		 * deselect all plugins depending upon entry after a warning.
		 * If user cancels, reinstate install field of entry
		 * @param entry	the entry that would be no more installed
		 * @return	confirmed that deselection may proceed
		 */
		private boolean deselectDependents(Entry entry)
		{
			Entry[] dependents = entry.getTransitiveDependents();

			if (dependents.length == 0)
				return true;

			String[] args = { entry.name };
			int result = GUIUtilities.listConfirm(
				window,"plugin-manager.dependency",
				args,dependents);
			if (result != JOptionPane.OK_OPTION)
			{
				entry.install = true;
				return false;
			}

			for(Entry dependent: dependents)
			{
				 dependent.install = false;
				 dependent.checked = false;
				 // must setInstall() to remove the
				 // plugin from its dependencies 'dependents' list
				 // so that they can be unchecked if no longer required
				 updateDeps(dependent);
			}
			return true;
		} //}}}

		//{{{ setValueAt() method
		@Override
		public void setValueAt(Object aValue, int row, int column)
		{
			if (column != 0) return;

			Entry entry = filteredEntries.get(row);

			boolean before = entry.install;
			entry.install = Boolean.TRUE.equals(aValue);
			if (before == entry.install) return;

			// deselect after a warning if entry is required
			// by some other selected plugin.
			// If the user cancelled, don't proceed
			if (!entry.install && !deselectDependents(entry))
			{
				return;
			}

			// checked is set after deselectDependents to prevent
			// override when the user cancelled deselectDependents
			entry.checked = entry.install;

			updateDeps(entry);

			/* prune entries to install to keep only the entries
			 * really checked by the user.
			 * Removes dependencies no longer required and such */

			List<Entry> selected = new ArrayList<Entry>(entries.size());
			for(Entry temp: entries)
			{
				if(temp.install)selected.add(temp);
			}

			List<Entry> toRemove = new ArrayList<Entry>(selected.size());
			boolean changed;
			do{
				changed = false;
				for(Entry temp: selected)
				{
					temp.dependents.removeAll(toRemove);
					if(!temp.checked && temp.dependents.isEmpty())
					{
						toRemove.add(temp);
						temp.install = false;
						changed = true;
					}
				}
				selected.removeAll(toRemove);
			}while(changed);

			updateFilteredEntries();
			// make the row selected after updated filtering
			for(int i=0; i< filteredEntries.size(); i++)
			{
				if(entry == filteredEntries.get(i))
				{
					table.setRowSelectionInterval(i, i);
					break;
				}
			}
		} //}}}

		//{{{ updateDeps() method
		/***
		 * recursively add dependencies to install,
		 * or remove entry from its dependencies' dependents list
		 * @param entry	entry whose install field has been set
		 */
		private void updateDeps(Entry entry)
		{

			List<PluginList.Dependency> deps = entry.plugin.getCompatibleBranch().deps;

			for (PluginList.Dependency dep: deps)
			{
				if ("plugin".equals(dep.what))
				{
					for (Entry temp: entries)
					{
						if (temp.plugin == dep.plugin)
						{
							if (entry.install)
							{
								temp.dependents.add(entry);
								if(!temp.install)
								{
									if(temp.installedVersion == null)
									{
										temp.install = true;
									}
									else if(!temp.plugin.loaded)
									{
										temp.install = true;
									}
									updateDeps(temp);
								}
							}
							else
							{
								temp.dependents.remove(entry);
							}
							break;
						}
					}
				}
			}
		} //}}}

		//{{{ sort() method
		public void sort(int type)
		{
			Set<String> savedChecked = new HashSet<String>();
			Set<String> savedSelection = new HashSet<String>();
			saveSelection(savedChecked,savedSelection);

			if (sortType != type)
			{
				sortDirection = 1;
			}
			sortType = type;

			Collections.sort(entries,new EntryCompare(type, sortDirection));
			updateFilteredEntries();
			restoreSelection(savedChecked,savedSelection);
			table.getTableHeader().repaint();
		}
		//}}}

		//{{{ update() method
		public void update()
		{
			Set<String> savedChecked = new HashSet<String>();
			Set<String> savedSelection = new HashSet<String>();
			saveSelection(savedChecked,savedSelection);

			PluginList pluginList = window.getPluginList();

			if (pluginList == null) return;

			entries.clear();

			for(PluginList.PluginSet set : pluginList.pluginSets)
			{
				for(int j = 0; j < set.plugins.size(); j++)
				{
					PluginList.Plugin plugin = pluginList.pluginHash.get(set.plugins.get(j));
					PluginList.Branch branch = plugin.getCompatibleBranch();
					String installedVersion =
						plugin.getInstalledVersion();
					if (updates)
					{
						if(branch != null
							&& branch.canSatisfyDependencies()
							&& installedVersion != null
							&& StandardUtilities.compareStrings(branch.version,
							installedVersion,false) > 0)
						{
							entries.add(new Entry(plugin, set.name));
						}
					}
					else
					{
						if(plugin.canBeInstalled())
							entries.add(new Entry(plugin,set.name));
					}
				}
			}

			sort(sortType);
			restoreSelection(savedChecked, savedSelection);
		} //}}}

		//{{{ saveSelection() method
		public void saveSelection(Set<String> savedChecked, Set<String> savedSelection)
		{
			if (entries.isEmpty())
				return;
			for (int i=0, c=getRowCount() ; i<c ; i++)
			{
				if ((Boolean)getValueAt(i,0))
				{
					savedChecked.add(filteredEntries.get(i).toString());
				}
			}
			int[] rows = table.getSelectedRows();
			for (int row: rows)
			{
				savedSelection.add(filteredEntries.get(row).toString());
			}
		} //}}}

		//{{{ clearSelection() method
		public void clearSelection()
		{
			restoreSelection(Collections.<String>emptySet(), Collections.<String>emptySet());
		} //}}}

		//{{{ restoreSelection() method
		public void restoreSelection(Set<String> savedChecked, Set<String> savedSelection)
		{
			for (int i=0, c=getRowCount() ; i<c ; i++)
			{
				Object obj = filteredEntries.get(i);
				String name = obj.toString();
				if (obj instanceof Entry)
				{
					name = ((Entry)obj).plugin.jar;
				}
				if (pluginSet.contains(name))
					setValueAt(true, i, 0);
				else setValueAt(savedChecked.contains(name), i, 0);
			}
			if (table == null) return;

			table.setColumnSelectionInterval(0,0);
			if (!savedSelection.isEmpty())
			{
				int i = 0;
				int rowCount = getRowCount();
				for ( ; i<rowCount ; i++)
				{
					String name = filteredEntries.get(i).toString();
					if (savedSelection.contains(name))
					{
						table.setRowSelectionInterval(i,i);
						break;
					}
				}
				ListSelectionModel lsm = table.getSelectionModel();
				for ( ; i<rowCount ; i++)
				{
					String name = filteredEntries.get(i).toString();
					if (savedSelection.contains(name))
					{
						lsm.addSelectionInterval(i,i);
					}
				}
			}
			else
			{
				if (table.getRowCount() != 0)
					table.setRowSelectionInterval(0,0);
				JScrollBar scrollbar = scrollpane.getVerticalScrollBar();
				scrollbar.setValue(scrollbar.getMinimum());
			}
		} //}}}
	} //}}}

	//{{{ Entry class
	private static class Entry
	{
		String name, installedVersion, version, author, date, description, set, dependencies;

		long timestamp;
		int size;
		boolean checked;
		boolean install;
		PluginList.Plugin plugin;
		List<Entry> dependents = new LinkedList<Entry>();

		Entry(PluginList.Plugin plugin, String set)
		{
			PluginList.Branch branch = plugin.getCompatibleBranch();
			boolean downloadSource = jEdit.getBooleanProperty("plugin-manager.downloadSource");
			int size = downloadSource ? branch.downloadSourceSize : branch.downloadSize;

			this.name = plugin.name;
			this.author = plugin.author;
			this.installedVersion = plugin.getInstalledVersion();
			this.version = branch.version;
			this.size = size;
			this.date = branch.date;
			this.description = plugin.description;
			this.dependencies = branch.depsToString();
			this.set = set;
			this.install = false;
			this.checked = false;
			this.plugin = plugin;
			SimpleDateFormat format = new SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH);
			try
			{
				timestamp = format.parse(date).getTime();
			}
			catch (ParseException e)
			{
				Log.log(Log.ERROR, this, e);
			}
		}

		private void getTransitiveDependents(List<Entry> list)
		{
			for (Entry entry : dependents)
			{
				if (entry.install && !list.contains(entry))
				{
					list.add(entry);
					entry.getTransitiveDependents(list);
				}
			}
		}

		Entry[] getTransitiveDependents()
		{
			List<Entry> list = new ArrayList<Entry>();
			getTransitiveDependents(list);
			Entry[] array = list.toArray(new Entry[list.size()]);
			Arrays.sort(array,new StandardUtilities.StringCompare<Entry>(true));
			return array;
		}

		@Override
		public String toString()
		{
			return name;
		}
	} //}}}

	//{{{ PluginInfoBox class
	/**
	 * TODO: refactor to use the PluginDetailPanel?
	 */
	private class PluginInfoBox extends JEditorPane implements ListSelectionListener
	{
		PluginInfoBox()
		{
			setBackground(jEdit.getColorProperty("view.bgColor"));
			setForeground(jEdit.getColorProperty("view.fgColor"));
			putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
			setContentType("text/html");
			setEditable(false);
			table.getSelectionModel().addListSelectionListener(this);
		}


		@Override
		public void valueChanged(ListSelectionEvent e)
		{
			String text = getText();
			if (table.getSelectedRowCount() == 1)
			{
				Entry entry = (Entry) pluginModel.filteredEntries
					.get(table.getSelectedRow());
				String pattern = "<b>{0}</b>: {1}<br><b>{2}</b>: {3}<br><br>{4}";
				List<String> params = new ArrayList<String>();
				params.add(jEdit.getProperty("install-plugins.info.author", "Author"));
				params.add(entry.author);
				params.add(jEdit.getProperty("install-plugins.info.released", "Released"));
				params.add(entry.date);
				params.add(entry.description);
				if (entry.dependencies != null && !entry.dependencies.isEmpty())
				{
					pattern += "<br><br><b>{5}</b>:<br>{6}";
					params.add(jEdit.getProperty("install-plugins.info.depends", "Depends on"));
					StringList sl = StringList.split(entry.dependencies, "\n");
					params.add(sl.join(", "));
				}
				text = MessageFormat.format(pattern, params.toArray());
			}
			setText(text);
			setCaretPosition(0);
			putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
		}
	} //}}}

	//{{{ SizeLabel class
	private class SizeLabel extends JLabel implements TableModelListener
	{
		private int size;
		private int nbPlugins;

		SizeLabel()
		{
			update();
			pluginModel.addTableModelListener(this);
		}

		@Override
		public void tableChanged(TableModelEvent e)
		{
			if (e.getType() == TableModelEvent.UPDATE)
			{
				if(isDownloadingList())
					return;

				size = 0;
				nbPlugins = 0;
				int length = pluginModel.entries.size();
				for (int i = 0; i < length; i++)
				{
					Entry entry = (Entry)pluginModel
						.entries.get(i);
					if (entry.install)
					{
						nbPlugins++;
						size += entry.size;
					}
				}
				update();
			}
		}

		private void update()
		{
			Object[] args = {nbPlugins, formatSize(size)};
			setText(jEdit.getProperty("install-plugins.totalSize", args));
		}
	} //}}}

	//{{{ SelectallButton class
	private class SelectallButton extends JCheckBox implements ActionListener, TableModelListener
	{
		SelectallButton()
		{
			super(jEdit.getProperty("install-plugins.select-all"));
			addActionListener(this);
			pluginModel.addTableModelListener(this);
			// setEnabled(false);
		}

		@Override
		public void actionPerformed(ActionEvent evt)
		{
			pluginModel.setSelectAll(isSelected());
		}

		@Override
		public void tableChanged(TableModelEvent e)
		{
			if(isDownloadingList())
				return;

			setEnabled(pluginModel.getRowCount() != 0);
			if (e.getType() == TableModelEvent.UPDATE)
			{
				int length = pluginModel.getRowCount();
				for (int i = 0; i < length; i++)
					if (!((Boolean)pluginModel.getValueAt(i,0)).booleanValue())
					{
						setSelected(false);
						return;
					}
				if (length > 0)
					setSelected(true);
			}
		}
	} //}}}

	//{{{ StringMapHandler class
	/** For parsing the pluginset xml files into pluginSet */
	private class StringMapHandler extends DefaultHandler
	{
		StringMapHandler()
		{
			pluginSet.clear();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException
		{
			if ("plugin".equals(localName))
			{
				pluginSet.add(attrs.getValue("jar"));
			}
		}
	} //}}}

	//{{{ ChoosePluginSet class
	private class ChoosePluginSet extends RolloverButton implements ActionListener
	{
		private String path;

		//{{{ ChoosePluginSet constructor
		ChoosePluginSet()
		{
			setIcon(GUIUtilities.loadIcon(jEdit.getProperty("install-plugins.choose-plugin-set.icon")));
			addActionListener(this);
			updateUI();
		} //}}}

		//{{{ updateUI method
		@Override
		public void updateUI()
		{
			path = jEdit.getProperty(PluginManager.PROPERTY_PLUGINSET, "");
			if (path.length()<1) setToolTipText ("Click here to choose a predefined plugin set");
			else setToolTipText ("Choose pluginset (" + path + ')');
			super.updateUI();
		}//}}}

		//{{{ actionPerformed() method
		@Override
		public void actionPerformed(ActionEvent ae)
		{

			path = jEdit.getProperty(PluginManager.PROPERTY_PLUGINSET,
				jEdit.getSettingsDirectory() + File.separator);
			String[] selectedFiles = GUIUtilities.showVFSFileDialog(InstallPanel.this.window,
				jEdit.getActiveView(), path, VFSBrowser.OPEN_DIALOG, false);
			if (selectedFiles == null || selectedFiles.length != 1) return;

			path = selectedFiles[0];
			boolean success = loadPluginSet(path);
			if (success)
			{
				jEdit.setProperty(PluginManager.PROPERTY_PLUGINSET, path);
			}
			updateUI();
		} //}}}
	}//}}}

	//{{{ ClearPluginSet class
	private class ClearPluginSet extends RolloverButton implements ActionListener
	{
		//{{{ ClearPluginSet constructor
		ClearPluginSet()
		{
			setIcon(GUIUtilities.loadIcon(jEdit.getProperty("install-plugins.clear-plugin-set.icon")));
			setToolTipText("clear plugin set");
			addActionListener(this);
		} //}}}

		//{{{ actionPerformed() method
		@Override
		public void actionPerformed(ActionEvent e)
		{
			pluginSet.clear();
			pluginModel.clearSelection();
			jEdit.unsetProperty(PluginManager.PROPERTY_PLUGINSET);
			chooseButton.updateUI();
		} //}}}
	} //}}}

	//{{{ InstallButton class
	private class InstallButton extends JButton implements ActionListener, TableModelListener
	{
		InstallButton()
		{
			super(jEdit.getProperty("install-plugins.install"));
			pluginModel.addTableModelListener(this);
			addActionListener(this);
			setEnabled(false);
		}

		@Override
		public void actionPerformed(ActionEvent evt)
		{
			if(isDownloadingList())
				return;

			boolean downloadSource = jEdit.getBooleanProperty(
				"plugin-manager.downloadSource");
			boolean installUser = jEdit.getBooleanProperty(
				"plugin-manager.installUser");
			Roster roster = new Roster();
			String installDirectory;
			if(installUser)
			{
				installDirectory = MiscUtilities.constructPath(
					jEdit.getSettingsDirectory(),"jars");
			}
			else
			{
				installDirectory = MiscUtilities.constructPath(
					jEdit.getJEditHome(),"jars");
			}

			int length = pluginModel.entries.size();
			int instcount = 0;
			for (int i = 0; i < length; i++)
			{
				Entry entry = (Entry)pluginModel.entries.get(i);
				if (entry.install)
				{
					entry.plugin.install(roster,installDirectory,downloadSource, !entry.checked);
					if (updates)
						entry.plugin.getCompatibleBranch().satisfyDependencies(
						roster,installDirectory,downloadSource);
					instcount++;
				}
			}

			if(roster.isEmpty())
				return;

			boolean cancel = false;
			if (updates && roster.getOperationCount() > instcount)
				if (GUIUtilities.confirm(window,
					"install-plugins.depend",
					null,
					JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.CANCEL_OPTION)
					cancel = true;

			if (!cancel)
			{
				new PluginManagerProgress(window,roster);

				roster.performOperationsInAWTThread(window);
				pluginModel.update();
			}
		}

		@Override
		public void tableChanged(TableModelEvent e)
		{
			if(isDownloadingList())
				return;

			if (e.getType() == TableModelEvent.UPDATE)
			{
				int length = pluginModel.getRowCount();
				for (int i = 0; i < length; i++)
					if (((Boolean)pluginModel.getValueAt(i,0)).booleanValue())
					{
						setEnabled(true);
						return;
					}
				setEnabled(false);
			}
		}
	} //}}}

	//{{{ EntryCompare class
	private static class EntryCompare implements Comparator<Entry>
	{
		private static final int COLUMN_INSTALL = 0;
		private static final int COLUMN_NAME = 1;
		private static final int COLUMN_CATEGORY = 2;
		private static final int COLUMN_VERSION = 3;
		private static final int COLUMN_SIZE = 4;
		private static final int COLUMN_RELEASE = 5;

		private final int type;

		/** 1=up, -1=down */
		private final int sortDirection;

		EntryCompare(int type, int sortDirection)
		{
			this.type = type;
			this.sortDirection = sortDirection;
		}

		@Override
		public int compare(Entry e1, Entry e2)
		{
			int result;

			switch (type)
			{
				case COLUMN_INSTALL:
					result = (e1.install == e2.install) ? 0 : (e1.install ? 1 : -1);
					break;
				case COLUMN_NAME:
					result = e1.name.compareToIgnoreCase(e2.name);
					break;
				case COLUMN_CATEGORY:
					result = e1.set.compareToIgnoreCase(e2.set);
					if (result == 0)
					{
						result = e1.name.compareToIgnoreCase(e2.name);
					}
					break;
				case COLUMN_VERSION:
					result = StandardUtilities.compareStrings(e1.version, e2.version, true);
					break;
				case COLUMN_SIZE:
					result = (e1.size < e2.size)
						 ? -1
						 : ((e1.size == e2.size)
						    ? 0
						    : 1);
					break;
				case COLUMN_RELEASE:
					result = (e1.timestamp < e2.timestamp)
						 ? -1
						 : ((e1.timestamp == e2.timestamp)
						    ? 0
						    : 1);
					break;
				default:
					result = 0;
			}
			return result * sortDirection;
		}
	} //}}}

	//{{{ HeaderMouseHandler class
	private class HeaderMouseHandler extends MouseAdapter
	{
		@Override
		public void mouseClicked(MouseEvent evt)
		{
			int column = table.getTableHeader().columnAtPoint(evt.getPoint());
			pluginModel.sortDirection *= -1;
			pluginModel.sort(column);
		}
	} //}}}

	//{{{ TextRenderer
	private static class TextRenderer extends DefaultTableCellRenderer
	{
		private final DefaultTableCellRenderer tcr;

		TextRenderer(DefaultTableCellRenderer tcr)
		{
			this.tcr = tcr;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column)
		{
			if (column == 5)
				tcr.setHorizontalAlignment(TRAILING);
			else
				tcr.setHorizontalAlignment(LEADING);
			return tcr.getTableCellRendererComponent(table,value,isSelected,false,row,column);
		}
	} //}}}

	//{{{ KeyboardAction class
	private class KeyboardAction extends AbstractAction
	{
		private KeyboardCommand command = KeyboardCommand.NONE;

		KeyboardAction(KeyboardCommand command)
		{
			this.command = command;
		}

		@Override
		public void actionPerformed(ActionEvent evt)
		{
			switch (command)
			{
			case TAB_OUT_FORWARD:
				KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent();
				break;
			case TAB_OUT_BACK:
				KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent();
				break;
			case EDIT_PLUGIN:
				int[] rows = table.getSelectedRows();
				Object[] state = new Object[rows.length];
				for (int i=0 ; i<rows.length ; i++)
				{
					state[i] = pluginModel.getValueAt(rows[i],0);
				}
				for (int i=0 ; i<rows.length ; i++)
				{
					pluginModel.setValueAt(state[i].equals(Boolean.FALSE),rows[i],0);
				}
				break;
			case CLOSE_PLUGIN_MANAGER:
				window.ok();
				break;
			default:
				throw new InternalError();
			}
		}
	} //}}}

	//{{{ TableFocusHandler class
	private class TableFocusHandler extends FocusAdapter
	{
		@Override
		public void focusGained(FocusEvent fe)
		{
			if (-1 == table.getSelectedRow() && table.getRowCount() > 0)
			{
				table.setRowSelectionInterval(0,0);
				JScrollBar scrollbar = scrollpane.getVerticalScrollBar();
				scrollbar.setValue(scrollbar.getMinimum());
			}
			if (-1 == table.getSelectedColumn())
			{
				table.setColumnSelectionInterval(0,0);
			}
		}
	} //}}}

	//{{{ HeaderRenderer
	private static class HeaderRenderer extends DefaultTableCellRenderer
	{
		private final DefaultTableCellRenderer tcr;

		HeaderRenderer(DefaultTableCellRenderer tcr)
		{
			this.tcr = tcr;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
							       boolean isSelected, boolean hasFocus,
							       int row, int column)
		{
			JLabel l = (JLabel)tcr.getTableCellRendererComponent(table,value,isSelected,hasFocus,row,column);
			PluginTableModel model = (PluginTableModel) table.getModel();
			Icon icon = (column == model.sortType)
				? (model.sortDirection == 1) ? ASC_ICON : DESC_ICON
				: null;
			l.setIcon(icon);
			return l;
		}
	} //}}}

	//}}}

	static final Icon ASC_ICON  = GUIUtilities.loadIcon("arrow-asc.png");
	static final Icon DESC_ICON = GUIUtilities.loadIcon("arrow-desc.png");
}
