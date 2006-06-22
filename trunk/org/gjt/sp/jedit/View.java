/*
 * View.java - jEdit view
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1998, 2004 Slava Pestov
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

package org.gjt.sp.jedit;

//{{{ Imports
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.Socket;
import java.util.*;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.util.Log;
//}}}

/**
 * A <code>View</code> is jEdit's top-level frame window.<p>
 *
 * In a BeanShell script, you can obtain the current view instance from the
 * <code>view</code> variable.<p>
 *
 * The largest component it contains is an {@link EditPane} that in turn
 * contains a {@link org.gjt.sp.jedit.textarea.JEditTextArea} that displays a
 * {@link Buffer}.
 * A view can have more than one edit pane in a split window configuration.
 * A view also contains a menu bar, an optional toolbar and other window
 * decorations, as well as docked windows.<p>
 *
 * The <b>View</b> class performs two important operations
 * dealing with plugins: creating plugin menu items, and managing dockable
 * windows.
 *
 * <ul>
 * <li>When a view is being created, its initialization routine
 * iterates through the collection of loaded plugins and constructs the
 * <b>Plugins</b> menu using the properties as specified in the
 * {@link EditPlugin} class.</li>
 * <li>The view also creates and initializes a
 * {@link org.gjt.sp.jedit.gui.DockableWindowManager}
 * object.  This object is
 * responsible for creating, closing and managing dockable windows.</li>
 * </ul>
 *
 * This class does not have a public constructor.
 * Views can be opened and closed using methods in the <code>jEdit</code>
 * class.
 *
 * @see org.gjt.sp.jedit.jEdit#newView(View)
 * @see org.gjt.sp.jedit.jEdit#newView(View,Buffer)
 * @see org.gjt.sp.jedit.jEdit#newView(View,Buffer,boolean)
 * @see org.gjt.sp.jedit.jEdit#closeView(View)
 *
 * @author Slava Pestov
 * @author John Gellene (API documentation)
 * @version $Id$
 */
public class View extends JFrame implements EBComponent
{
	//{{{ User interface

	//{{{ ToolBar-related constants

	//{{{ Groups
	/**
	 * The group of tool bars above the DockableWindowManager
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int TOP_GROUP = 0;

	/**
	 * The group of tool bars below the DockableWindowManager
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BOTTOM_GROUP = 1;
	public static final int DEFAULT_GROUP = TOP_GROUP;
	//}}}

	//{{{ Layers

	// Common layers
	/**
	 * The highest possible layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int TOP_LAYER = Integer.MAX_VALUE;

	/**
	 * The default layer for tool bars with no preference.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int DEFAULT_LAYER = 0;

	/**
	 * The lowest possible layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BOTTOM_LAYER = Integer.MIN_VALUE;

	// Layers for top group
	/**
	 * Above system tool bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int ABOVE_SYSTEM_BAR_LAYER = 150;

	/**
	 * System tool bar layer.
	 * jEdit uses this for the main tool bar.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int SYSTEM_BAR_LAYER = 100;

	/**
	 * Below system tool bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BELOW_SYSTEM_BAR_LAYER = 75;

	/**
	 * Search bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int SEARCH_BAR_LAYER = 75;

	/**
	 * Below search bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BELOW_SEARCH_BAR_LAYER = 50;

	// Layers for bottom group
	/**
	 * @deprecated Status bar no longer added as a tool bar.
	 */
	public static final int ABOVE_ACTION_BAR_LAYER = -50;

	/**
	 * Action bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.2pre1
	 */
	public static final int ACTION_BAR_LAYER = -75;

	/**
	 * Status bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.2pre1
	 */
	public static final int STATUS_BAR_LAYER = -100;

	/**
	 * Status bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.2pre1
	 */
	public static final int BELOW_STATUS_BAR_LAYER = -150;
	//}}}

	//}}}

	//{{{ getDockableWindowManager() method
	/**
	 * Returns the dockable window manager associated with this view.
	 * @since jEdit 2.6pre3
	 */
	public DockableWindowManager getDockableWindowManager()
	{
		return dockableWindowManager;
	} //}}}

	//{{{ getToolBar() method
	/**
	 * Returns the view's tool bar.
	 * @since jEdit 4.2pre1
	 */
	public Box getToolBar()
	{
		return toolBar;
	} //}}}

	//{{{ addToolBar() method
	/**
	 * Adds a tool bar to this view.
	 * @param toolBar The tool bar
	 */
	public void addToolBar(Component toolBar)
	{
		addToolBar(DEFAULT_GROUP, DEFAULT_LAYER, toolBar);
	} //}}}

	//{{{ addToolBar() method
	/**
	 * Adds a tool bar to this view.
	 * @param group The tool bar group to add to
	 * @param toolBar The tool bar
	 * @see org.gjt.sp.jedit.gui.ToolBarManager
	 * @since jEdit 4.0pre7
	 */
	public void addToolBar(int group, Component toolBar)
	{
		addToolBar(group, DEFAULT_LAYER, toolBar);
	} //}}}

	//{{{ addToolBar() method
	/**
	 * Adds a tool bar to this view.
	 * @param group The tool bar group to add to
	 * @param layer The layer of the group to add to
	 * @param toolBar The tool bar
	 * @see org.gjt.sp.jedit.gui.ToolBarManager
	 * @since jEdit 4.0pre7
	 */
	public void addToolBar(int group, int layer, Component toolBar)
	{
		toolBarManager.addToolBar(group, layer, toolBar);
		getRootPane().revalidate();
	} //}}}

	//{{{ removeToolBar() method
	/**
	 * Removes a tool bar from this view.
	 * @param toolBar The tool bar
	 */
	public void removeToolBar(Component toolBar)
	{
		if (toolBarManager == null) return;
		if (toolBar == null) return;
		toolBarManager.removeToolBar(toolBar);
		getRootPane().revalidate();
	} //}}}

	//{{{ showWaitCursor() method
	/**
	 * Shows the wait cursor. This method and
	 * {@link #hideWaitCursor()} are implemented using a reference
	 * count of requests for wait cursors, so that nested calls work
	 * correctly; however, you should be careful to use these methods in
	 * tandem.<p>
	 *
	 * To ensure that {@link #hideWaitCursor()} is always called
	 * after a {@link #showWaitCursor()}, use a
	 * <code>try</code>/<code>finally</code> block, like this:
	 * <pre>try
	 *{
	 *    view.showWaitCursor();
	 *    // ...
	 *}
	 *finally
	 *{
	 *    view.hideWaitCursor();
	 *}</pre>
	 */
	public synchronized void showWaitCursor()
	{
		if(waitCount++ == 0)
		{
			Cursor cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
			setCursor(cursor);
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				EditPane editPane = editPanes[i];
				editPane.getTextArea().getPainter()
					.setCursor(cursor);
			}
		}
	} //}}}

	//{{{ hideWaitCursor() method
	/**
	 * Hides the wait cursor.
	 */
	public synchronized void hideWaitCursor()
	{
		if(waitCount > 0)
			waitCount--;

		if(waitCount == 0)
		{
			// still needed even though glass pane
			// has a wait cursor
			Cursor cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
			setCursor(cursor);
			cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				EditPane editPane = editPanes[i];
				editPane.getTextArea().getPainter()
					.setCursor(cursor);
			}
		}
	} //}}}

	//{{{ getSearchBar() method
	/**
	 * Returns the search bar.
	 * @since jEdit 2.4pre4
	 */
	public final SearchBar getSearchBar()
	{
		return searchBar;
	} //}}}

	//{{{ getActionBar() method
	/**
	 * Returns the action bar.
	 * @since jEdit 4.2pre3
	 */
	public final ActionBar getActionBar()
	{
		return actionBar;
	} //}}}

	//{{{ getStatus() method
	/**
	 * Returns the status bar. The
	 * {@link org.gjt.sp.jedit.gui.StatusBar#setMessage(String)} and
	 * {@link org.gjt.sp.jedit.gui.StatusBar#setMessageAndClear(String)} methods can
	 * be called on the return value of this method to display status
	 * information to the user.
	 * @since jEdit 3.2pre2
	 */
	public StatusBar getStatus()
	{
		return status;
	} //}}}

	//{{{ quickIncrementalSearch() method
	/**
	 * Quick search.
	 * @since jEdit 4.0pre3
	 */
	public void quickIncrementalSearch(boolean word)
	{
		if(searchBar == null)
			searchBar = new SearchBar(this,true);
		if(searchBar.getParent() == null)
			addToolBar(TOP_GROUP,SEARCH_BAR_LAYER,searchBar);

		searchBar.setHyperSearch(false);

		JEditTextArea textArea = getTextArea();

		if(word)
		{
			String text = textArea.getSelectedText();
			if(text == null)
			{
				textArea.selectWord();
				text = textArea.getSelectedText();
			}
			else if(text.indexOf('\n') != -1)
				text = null;

			if(text != null && SearchAndReplace.getRegexp())
				text = SearchAndReplace.escapeRegexp(text,false);

			searchBar.getField().setText(text);
		}

		searchBar.getField().requestFocus();
		searchBar.getField().selectAll();
	} //}}}

	//{{{ quickHyperSearch() method
	/**
	 * Quick HyperSearch.
	 * @since jEdit 4.0pre3
	 */
	public void quickHyperSearch(boolean word)
	{
		JEditTextArea textArea = getTextArea();

		if(word)
		{
			String text = textArea.getSelectedText();
			if(text == null)
			{
				textArea.selectWord();
				text = textArea.getSelectedText();
			}

			if(text != null && text.indexOf('\n') == -1)
			{
				if(SearchAndReplace.getRegexp())
				{
					text = SearchAndReplace.escapeRegexp(
						text,false);
				}

				HistoryModel.getModel("find").addItem(text);
				SearchAndReplace.setSearchString(text);
				SearchAndReplace.setSearchFileSet(new CurrentBufferSet());
				SearchAndReplace.hyperSearch(this);

				return;
			}
		}

		if(searchBar == null)
			searchBar = new SearchBar(this,true);
		if(searchBar.getParent() == null)
			addToolBar(TOP_GROUP,SEARCH_BAR_LAYER,searchBar);

		searchBar.setHyperSearch(true);
		searchBar.getField().setText(null);
		searchBar.getField().requestFocus();
		searchBar.getField().selectAll();
	} //}}}

	//{{{ actionBar() method
	/**
	 * Shows the action bar if needed, and sends keyboard focus there.
	 * @since jEdit 4.2pre1
	 */
	public void actionBar()
	{
		if(actionBar == null)
			actionBar = new ActionBar(this,true);
		if(actionBar.getParent() == null)
			addToolBar(BOTTOM_GROUP,ACTION_BAR_LAYER,actionBar);

		actionBar.goToActionBar();
	} //}}}

	//}}}

	//{{{ Input handling

	//{{{ getKeyEventInterceptor() method
	/**
	 * Returns the listener that will handle all key events in this
	 * view, if any.
	 */
	public KeyListener getKeyEventInterceptor()
	{
		return keyEventInterceptor;
	} //}}}

	//{{{ setKeyEventInterceptor() method
	/**
	 * Sets the listener that will handle all key events in this
	 * view. For example, the complete word command uses this so
	 * that all key events are passed to the word list popup while
	 * it is visible.
	 * @param listener The key event interceptor.
	 */
	public void setKeyEventInterceptor(KeyListener listener)
	{
		this.keyEventInterceptor = listener;
	} //}}}

	//{{{ getInputHandler() method
	/**
	 * Returns the input handler.
	 */
	public InputHandler getInputHandler()
	{
		return inputHandler;
	} //}}}

	//{{{ setInputHandler() method
	/**
	 * Sets the input handler.
	 * @param inputHandler The new input handler
	 */
	public void setInputHandler(InputHandler inputHandler)
	{
		this.inputHandler = inputHandler;
	} //}}}

	//{{{ getMacroRecorder() method
	/**
	 * Returns the macro recorder.
	 */
	public Macros.Recorder getMacroRecorder()
	{
		return recorder;
	} //}}}

	//{{{ setMacroRecorder() method
	/**
	 * Sets the macro recorder.
	 * @param recorder The macro recorder
	 */
	public void setMacroRecorder(Macros.Recorder recorder)
	{
		this.recorder = recorder;
	} //}}}

	//{{{ processKeyEvent() method
	/**
	 * Forwards key events directly to the input handler.
	 * This is slightly faster than using a KeyListener
	 * because some Swing overhead is avoided.
	 */
	public void processKeyEvent(KeyEvent evt)
	{
		processKeyEvent(evt,VIEW);
	} //}}}

	//{{{ processKeyEvent() method
	/**
	 * Forwards key events directly to the input handler.
	 * This is slightly faster than using a KeyListener
	 * because some Swing overhead is avoided.
	 */
	public void processKeyEvent(KeyEvent evt, boolean calledFromTextArea)
	{
		processKeyEvent(evt,calledFromTextArea
			? TEXT_AREA
			: VIEW);
	} //}}}

	//{{{ processKeyEvent() method
	public static final int VIEW = 0;
	public static final int TEXT_AREA = 1;
	public static final int ACTION_BAR = 2;
	/**
	 * Forwards key events directly to the input handler.
	 * This is slightly faster than using a KeyListener
	 * because some Swing overhead is avoided.
	 */
	public void processKeyEvent(KeyEvent evt, int from)
	{
		if(Debug.DUMP_KEY_EVENTS)
		{
			Log.log(Log.DEBUG,this,"Key event                 : "
				+ GrabKeyDialog.toString(evt) + " from " + from);
		}

		if(getTextArea().hasFocus() && from == VIEW)
			return;

		evt = _preprocessKeyEvent(evt);
		if(evt == null)
			return;

		if(Debug.DUMP_KEY_EVENTS)
		{
			Log.log(Log.DEBUG,this,"Key event after workaround: "
				+ GrabKeyDialog.toString(evt) + " from " + from);
		}

		switch(evt.getID())
		{
		case KeyEvent.KEY_TYPED:
			boolean focusOnTextArea = false;
			// if the user pressed eg C+e n n in the
			// search bar we want focus to go back there
			// after the prefix is done
			if(prefixFocusOwner != null)
			{
				if(prefixFocusOwner.isShowing())
				{
					prefixFocusOwner.requestFocus();
					focusOnTextArea = true;
				}
			}

			if(keyEventInterceptor != null)
				keyEventInterceptor.keyTyped(evt);
			else if(from == ACTION_BAR
				|| Debug.GLOBAL_SHORTCUTS_FOR_DOCKED_DOCKABLES
				|| inputHandler.isPrefixActive()
				|| getTextArea().hasFocus())
			{
				KeyEventTranslator.Key keyStroke
					= KeyEventTranslator
					.translateKeyEvent(evt);
				if(keyStroke != null)
				{
					if(Debug.DUMP_KEY_EVENTS)
					{
						Log.log(Log.DEBUG,this,
							"Translated (key type ): "
							+ keyStroke + " from " + from);
					}
					if(inputHandler.handleKey(keyStroke))
						evt.consume();
				}
			}

			// we might have been closed as a result of
			// the above
			if(isClosed())
				return;

			// this is a weird hack.
			// we don't want C+e a to insert 'a' in the
			// search bar if the search bar has focus...
			if(inputHandler.isPrefixActive())
			{
				if(getFocusOwner() instanceof JTextComponent)
				{
					prefixFocusOwner = getFocusOwner();
					getTextArea().requestFocus();
				}
				else if(focusOnTextArea)
				{
					getTextArea().requestFocus();
				}
				else
				{
					prefixFocusOwner = null;
				}
			}
			else
			{
				prefixFocusOwner = null;
			}

			break;
		case KeyEvent.KEY_PRESSED:
			if(keyEventInterceptor != null)
				keyEventInterceptor.keyPressed(evt);
			else if(KeyEventWorkaround.isBindable(evt.getKeyCode()))
			{
				/* boolean */ focusOnTextArea = false;
				if(prefixFocusOwner != null)
				{
					if(prefixFocusOwner.isShowing())
					{
						prefixFocusOwner.requestFocus();
						focusOnTextArea = true;
					}
					prefixFocusOwner = null;
				}

				KeyEventTranslator.Key keyStroke
					= KeyEventTranslator
					.translateKeyEvent(evt);
				if(keyStroke != null)
				{
					if(Debug.DUMP_KEY_EVENTS)
					{
						Log.log(Log.DEBUG,this,
							"Translated (key press): "
							+ keyStroke + " from " + from);
					}
					if(inputHandler.handleKey(keyStroke))
						evt.consume();
				}

				// we might have been closed as a result of
				// the above
				if(isClosed())
					return;

				// this is a weird hack.
				// we don't want C+e a to insert 'a' in the
				// search bar if the search bar has focus...
				if(inputHandler.isPrefixActive())
				{
					if(getFocusOwner() instanceof JTextComponent)
					{
						prefixFocusOwner = getFocusOwner();
						getTextArea().requestFocus();
					}
					else if(focusOnTextArea)
					{
						getTextArea().requestFocus();
					}
					else
					{
						prefixFocusOwner = null;
					}
				}
				else
				{
					prefixFocusOwner = null;
				}
			}
			break;
		case KeyEvent.KEY_RELEASED:
			if(keyEventInterceptor != null)
				keyEventInterceptor.keyReleased(evt);
			break;
		}

		if(!evt.isConsumed())
			super.processKeyEvent(evt);
	} //}}}

	//}}}

	//{{{ Buffers, edit panes, split panes

	//{{{ splitHorizontally() method
	/**
	 * Splits the view horizontally.
	 * @since jEdit 4.1pre2
	 */
	public EditPane splitHorizontally()
	{
		return split(JSplitPane.VERTICAL_SPLIT);
	} //}}}

	//{{{ splitVertically() method
	/**
	 * Splits the view vertically.
	 * @since jEdit 4.1pre2
	 */
	public EditPane splitVertically()
	{
		return split(JSplitPane.HORIZONTAL_SPLIT);
	} //}}}

	//{{{ split() method
	/**
	 * Splits the view.
	 * @since jEdit 4.1pre2
	 */
	public EditPane split(int orientation)
	{
		PerspectiveManager.setPerspectiveDirty(true);

		editPane.saveCaretInfo();
		EditPane oldEditPane = editPane;
		setEditPane(createEditPane(oldEditPane.getBuffer()));
		editPane.loadCaretInfo();

		JComponent oldParent = (JComponent)oldEditPane.getParent();

		final JSplitPane newSplitPane = new JSplitPane(orientation);
		newSplitPane.setOneTouchExpandable(true);
		newSplitPane.setBorder(null);
		newSplitPane.setMinimumSize(new Dimension(0,0));
		newSplitPane.setResizeWeight(0.5);

		int parentSize = (orientation == JSplitPane.VERTICAL_SPLIT
			? oldEditPane.getHeight() : oldEditPane.getWidth());
		final int dividerPosition = (int)((parentSize
			- newSplitPane.getDividerSize()) * 0.5);
		newSplitPane.setDividerLocation(dividerPosition);

		if(oldParent instanceof JSplitPane)
		{
			JSplitPane oldSplitPane = (JSplitPane)oldParent;
			int dividerPos = oldSplitPane.getDividerLocation();

			Component left = oldSplitPane.getLeftComponent();

			if(left == oldEditPane)
				oldSplitPane.setLeftComponent(newSplitPane);
			else
				oldSplitPane.setRightComponent(newSplitPane);

			newSplitPane.setLeftComponent(oldEditPane);
			newSplitPane.setRightComponent(editPane);

			oldSplitPane.setDividerLocation(dividerPos);
		}
		else
		{
			this.splitPane = newSplitPane;

			newSplitPane.setLeftComponent(oldEditPane);
			newSplitPane.setRightComponent(editPane);

			oldParent.add(newSplitPane,0);
			oldParent.revalidate();
		}

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				newSplitPane.setDividerLocation(dividerPosition);
			}
		});

		editPane.focusOnTextArea();

		return editPane;
	} //}}}

	//{{{ unsplit() method
	/**
	 * Unsplits the view.
	 * @since jEdit 2.3pre2
	 */
	public void unsplit()
	{
		if(splitPane != null)
		{
			lastSplitConfig = getSplitConfig();

			PerspectiveManager.setPerspectiveDirty(true);

			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				EditPane _editPane = editPanes[i];
				if(editPane != _editPane)
					_editPane.close();
			}

			JComponent parent = (JComponent)splitPane.getParent();

			parent.remove(splitPane);
			parent.add(editPane,0);
			parent.revalidate();

			splitPane = null;
			updateTitle();

			editPane.focusOnTextArea();
		}
		else
			getToolkit().beep();
	} //}}}

	//{{{ unsplitCurrent() method
	/**
	 * Removes the current split.
	 * @since jEdit 2.3pre2
	 */
	public void unsplitCurrent()
	{
		if(splitPane != null)
		{
			lastSplitConfig = getSplitConfig();

			PerspectiveManager.setPerspectiveDirty(true);

			// find first split pane parenting current edit pane
			Component comp = editPane;
			while(!(comp instanceof JSplitPane))
			{
				comp = comp.getParent();
			}

			// get rid of any edit pane that is a child
			// of the current edit pane's parent splitter
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				EditPane _editPane = editPanes[i];
				if(GUIUtilities.isAncestorOf(comp,_editPane)
					&& _editPane != editPane)
					_editPane.close();
			}

			JComponent parent = (JComponent)comp.getParent();

			if(parent instanceof JSplitPane)
			{
				JSplitPane parentSplit = (JSplitPane)parent;
				int pos = parentSplit.getDividerLocation();
				if(parentSplit.getLeftComponent() == comp)
					parentSplit.setLeftComponent(editPane);
				else
					parentSplit.setRightComponent(editPane);
				parentSplit.setDividerLocation(pos);
			}
			else
			{
				parent.remove(comp);
				parent.add(editPane,0);
				splitPane = null;
			}

			parent.revalidate();

			updateTitle();

			editPane.focusOnTextArea();
		}
		else
			getToolkit().beep();
	} //}}}

	//{{{ resplit() method
	/**
	 * Restore the split configuration as it was before unsplitting.
	 *
	 * @since jEdit 4.3pre1
	 */
	public void resplit()
	{
		if(lastSplitConfig == null)
			getToolkit().beep();
		else
			setSplitConfig(null,lastSplitConfig);
	} //}}}

	//{{{ nextTextArea() method
	/**
	 * Moves keyboard focus to the next text area.
	 * @since jEdit 2.7pre4
	 */
	public void nextTextArea()
	{
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			if(editPane == editPanes[i])
			{
				if(i == editPanes.length - 1)
					editPanes[0].focusOnTextArea();
				else
					editPanes[i+1].focusOnTextArea();
				break;
			}
		}
	} //}}}

	//{{{ prevTextArea() method
	/**
	 * Moves keyboard focus to the previous text area.
	 * @since jEdit 2.7pre4
	 */
	public void prevTextArea()
	{
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			if(editPane == editPanes[i])
			{
				if(i == 0)
					editPanes[editPanes.length - 1].focusOnTextArea();
				else
					editPanes[i-1].focusOnTextArea();
				break;
			}
		}
	} //}}}

	//{{{ getSplitPane() method
	/**
	 * Returns the top-level split pane, if any.
	 * @since jEdit 2.3pre2
	 */
	public JSplitPane getSplitPane()
	{
		return splitPane;
	} //}}}

	//{{{ getBuffer() method
	/**
	 * Returns the current edit pane's buffer.
	 */
	public Buffer getBuffer()
	{
		if(editPane == null)
			return null;
		else
			return editPane.getBuffer();
	} //}}}

	//{{{ setBuffer() method
	/**
	 * Sets the current edit pane's buffer.
	 */
	public void setBuffer(Buffer buffer)
	{
		editPane.setBuffer(buffer);
	} //}}}

	//{{{ goToBuffer() method
	/**
	 * If this buffer is open in one of the view's edit panes, sets focus
	 * to that edit pane. Otherwise, opens the buffer in the currently
	 * active edit pane.
	 * @param buffer The buffer
	 * @since jEdit 4.2pre1
	 */
	public EditPane goToBuffer(Buffer buffer)
	{
		if(editPane.getBuffer() == buffer
			&& editPane.getTextArea().getVisibleLines() > 1)
		{
			editPane.focusOnTextArea();
			return editPane;
		}

		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			EditPane ep = editPanes[i];
			if(ep.getBuffer() == buffer
				/* ignore zero-height splits, etc */
				&& ep.getTextArea().getVisibleLines() > 1)
			{
				setEditPane(ep);
				ep.focusOnTextArea();
				return ep;
			}
		}

		setBuffer(buffer);
		return editPane;
	} //}}}

	//{{{ getTextArea() method
	/**
	 * Returns the current edit pane's text area.
	 */
	public JEditTextArea getTextArea()
	{
		if(editPane == null)
			return null;
		else
			return editPane.getTextArea();
	} //}}}

	//{{{ getEditPane() method
	/**
	 * Returns the current edit pane.
	 * @since jEdit 2.5pre2
	 */
	public EditPane getEditPane()
	{
		return editPane;
	} //}}}

	//{{{ getEditPanes() method
	/**
	 * Returns all edit panes.
	 * @since jEdit 2.5pre2
	 */
	public EditPane[] getEditPanes()
	{
		if(splitPane == null)
		{
			EditPane[] ep = { editPane };
			return ep;
		}
		else
		{
			Vector vec = new Vector();
			getEditPanes(vec,splitPane);
			EditPane[] ep = new EditPane[vec.size()];
			vec.copyInto(ep);
			return ep;
		}
	} //}}}

	//{{{ getViewConfig() method
	/**
	 * @since jEdit 4.2pre1
	 */
	public ViewConfig getViewConfig()
	{
		ViewConfig config = new ViewConfig();
		config.plainView = isPlainView();
		config.splitConfig = getSplitConfig();
		config.x = getX();
		config.y = getY();
		config.width = getWidth();
		config.height = getHeight();
		config.extState = getExtendedState();

		config.top = dockableWindowManager.getTopDockingArea().getCurrent();
		config.left = dockableWindowManager.getLeftDockingArea().getCurrent();
		config.bottom = dockableWindowManager.getBottomDockingArea().getCurrent();
		config.right = dockableWindowManager.getRightDockingArea().getCurrent();

		config.topPos = dockableWindowManager.getTopDockingArea().getDimension();
		config.leftPos = dockableWindowManager.getLeftDockingArea().getDimension();
		config.bottomPos = dockableWindowManager.getBottomDockingArea().getDimension();
		config.rightPos = dockableWindowManager.getRightDockingArea().getDimension();

		return config;
	} //}}}

	//}}}

	//{{{ isClosed() method
	/**
	 * Returns true if this view has been closed with
	 * {@link jEdit#closeView(View)}.
	 */
	public boolean isClosed()
	{
		return closed;
	} //}}}

	//{{{ isPlainView() method
	/**
	 * Returns true if this is an auxilliary view with no dockable windows.
	 * @since jEdit 4.1pre2
	 */
	public boolean isPlainView()
	{
		return plainView;
	} //}}}

	//{{{ getNext() method
	/**
	 * Returns the next view in the list.
	 */
	public View getNext()
	{
		return next;
	} //}}}

	//{{{ getPrev() method
	/**
	 * Returns the previous view in the list.
	 */
	public View getPrev()
	{
		return prev;
	} //}}}

	//{{{ handleMessage() method
	public void handleMessage(EBMessage msg)
	{
		if(msg instanceof PropertiesChanged)
			propertiesChanged();
		else if(msg instanceof SearchSettingsChanged)
		{
			if(searchBar != null)
				searchBar.update();
		}
		else if(msg instanceof BufferUpdate)
			handleBufferUpdate((BufferUpdate)msg);
		else if(msg instanceof EditPaneUpdate)
			handleEditPaneUpdate((EditPaneUpdate)msg);
	} //}}}

	//{{{ getMinimumSize() method
	public Dimension getMinimumSize()
	{
		return new Dimension(0,0);
	} //}}}

	//{{{ setWaitSocket() method
	/**
	 * This socket is closed when the buffer is closed.
	 */
	public void setWaitSocket(Socket waitSocket)
	{
		this.waitSocket = waitSocket;
	} //}}}

	//{{{ toString() method
	public String toString()
	{
		return getClass().getName() + "["
			+ (jEdit.getActiveView() == this
			? "active" : "inactive")
			+ "]";
	} //}}}

	//{{{ updateTitle() method
	/**
	 * Updates the title bar.
	 */
	public void updateTitle()
	{
		Vector buffers = new Vector();
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			Buffer buffer = editPanes[i].getBuffer();
			if(buffers.indexOf(buffer) == -1)
				buffers.addElement(buffer);
		}

		StringBuffer title = new StringBuffer();

		/* On Mac OS X, apps are not supposed to show their name in the
		title bar. */
		if(!OperatingSystem.isMacOS())
			title.append(jEdit.getProperty("view.title"));

		boolean unsavedChanges = false;

		for(int i = 0; i < buffers.size(); i++)
		{
			if(i != 0)
				title.append(", ");

			Buffer buffer = (Buffer)buffers.elementAt(i);
			title.append((showFullPath && !buffer.isNewFile())
				? buffer.getPath() : buffer.getName());
			if(buffer.isDirty())
			{
				unsavedChanges = true;
				title.append(jEdit.getProperty("view.title.dirty"));
			}
		}

		setTitle(title.toString());

		/* On MacOS X, the close box is shown in a different color if
		an app has unsaved changes. For details, see
		http://developer.apple.com/qa/qa2001/qa1146.html */
		final String WINDOW_MODIFIED = "windowModified";
		getRootPane().putClientProperty(WINDOW_MODIFIED,
			Boolean.valueOf(unsavedChanges));
	} //}}}

	//{{{ Package-private members
	View prev;
	View next;

	//{{{ View constructor
	View(Buffer buffer, ViewConfig config)
	{
		this.plainView = config.plainView;

		enableEvents(AWTEvent.KEY_EVENT_MASK);

		setIconImage(GUIUtilities.getEditorIcon());

		dockableWindowManager = new DockableWindowManager(this,
			DockableWindowFactory.getInstance(),config);

		topToolBars = new JPanel(new VariableGridLayout(
			VariableGridLayout.FIXED_NUM_COLUMNS,
			1));
		bottomToolBars = new JPanel(new VariableGridLayout(
			VariableGridLayout.FIXED_NUM_COLUMNS,
			1));

		toolBarManager = new ToolBarManager(topToolBars, bottomToolBars);

		status = new StatusBar(this);

		inputHandler = new DefaultInputHandler(this,(DefaultInputHandler)
			jEdit.getInputHandler());

		setSplitConfig(buffer,config.splitConfig);

		getContentPane().add(BorderLayout.CENTER,dockableWindowManager);

		dockableWindowManager.init();

		// tool bar and status bar gets added in propertiesChanged()
		// depending in the 'tool bar alternate layout' setting.
		propertiesChanged();

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowHandler());

		setFocusTraversalPolicy(new MyFocusTraversalPolicy());

		EditBus.addToBus(this);

		SearchDialog.preloadSearchDialog(this);
	} //}}}

	//{{{ close() method
	void close()
	{
		GUIUtilities.saveGeometry(this,plainView ? "plain-view" : "view");
		closed = true;

		// save dockable window geometry, and close 'em
		dockableWindowManager.close();

		EditBus.removeFromBus(this);
		dispose();

		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
			editPanes[i].close();

		// null some variables so that retaining references
		// to closed views won't hurt as much.
		toolBarManager = null;
		toolBar = null;
		searchBar = null;
		splitPane = null;
		inputHandler = null;
		recorder = null;

		getContentPane().removeAll();

		// notify clients with -wait
		if(waitSocket != null)
		{
			try
			{
				waitSocket.getOutputStream().write('\0');
				waitSocket.getOutputStream().flush();
				waitSocket.getInputStream().close();
				waitSocket.getOutputStream().close();
				waitSocket.close();
			}
			catch(IOException io)
			{
				//Log.log(Log.ERROR,this,io);
			}
		}
	} //}}}

	//}}}

	//{{{ Private members

	//{{{ Instance variables
	private boolean closed;

	private DockableWindowManager dockableWindowManager;

	private JPanel topToolBars;
	private JPanel bottomToolBars;
	private ToolBarManager toolBarManager;

	private Box toolBar;
	private SearchBar searchBar;
	private ActionBar actionBar;

	private EditPane editPane;
	private JSplitPane splitPane;
	private String lastSplitConfig;

	private StatusBar status;

	private KeyListener keyEventInterceptor;
	private InputHandler inputHandler;
	private Macros.Recorder recorder;
	private Component prefixFocusOwner;

	private int waitCount;

	private boolean showFullPath;

	private boolean plainView;

	private Socket waitSocket;
	//}}}

	//{{{ getEditPanes() method
	private void getEditPanes(Vector vec, Component comp)
	{
		if(comp instanceof EditPane)
			vec.addElement(comp);
		else if(comp instanceof JSplitPane)
		{
			JSplitPane split = (JSplitPane)comp;
			getEditPanes(vec,split.getLeftComponent());
			getEditPanes(vec,split.getRightComponent());
		}
	} //}}}

	//{{{ getSplitConfig() method
	private String getSplitConfig()
	{
		StringBuffer splitConfig = new StringBuffer();

		if(splitPane != null)
			getSplitConfig(splitPane,splitConfig);
		else
		{
			splitConfig.append('"');
			splitConfig.append(MiscUtilities.charsToEscapes(
				getBuffer().getPath()));
			splitConfig.append("\" buffer");
		}

		return splitConfig.toString();
	} //}}}

	//{{{ getSplitConfig() method
	/*
	 * The split config is recorded in a simple RPN "language".
	 */
	private void getSplitConfig(JSplitPane splitPane,
		StringBuffer splitConfig)
	{
		Component right = splitPane.getRightComponent();
		if(right instanceof JSplitPane)
			getSplitConfig((JSplitPane)right,splitConfig);
		else
		{
			splitConfig.append('"');
			splitConfig.append(MiscUtilities.charsToEscapes(
				((EditPane)right).getBuffer().getPath()));
			splitConfig.append("\" buffer");
		}

		splitConfig.append(' ');

		Component left = splitPane.getLeftComponent();
		if(left instanceof JSplitPane)
			getSplitConfig((JSplitPane)left,splitConfig);
		else
		{
			splitConfig.append('"');
			splitConfig.append(MiscUtilities.charsToEscapes(
				((EditPane)left).getBuffer().getPath()));
			splitConfig.append("\" buffer");
		}

		splitConfig.append(' ');
		splitConfig.append(splitPane.getDividerLocation());
		splitConfig.append(' ');
		splitConfig.append(splitPane.getOrientation()
			== JSplitPane.VERTICAL_SPLIT ? "vertical" : "horizontal");
	} //}}}

	//{{{ setSplitConfig() method
	private void setSplitConfig(Buffer buffer, String splitConfig)
	{
		if(editPane != null)
			dockableWindowManager.remove(editPane);

		if(splitPane != null)
			dockableWindowManager.remove(splitPane);

		try
		{
			Component comp = restoreSplitConfig(buffer,splitConfig);
			dockableWindowManager.add(comp,0);
		}
		catch(IOException e)
		{
			// this should never throw an exception.
			throw new InternalError();
		}

		dockableWindowManager.revalidate();
		dockableWindowManager.repaint();
	} //}}}

	//{{{ restoreSplitConfig() method
	private Component restoreSplitConfig(Buffer buffer, String splitConfig)
		throws IOException
	// this is where checked exceptions piss me off. this method only uses
	// a StringReader which can never throw an exception...
	{
		if(buffer != null)
			return (editPane = createEditPane(buffer));
		else if(splitConfig == null)
			return (editPane = createEditPane(jEdit.getFirstBuffer()));

		Buffer[] buffers = jEdit.getBuffers();

		Stack stack = new Stack();

		// we create a stream tokenizer for parsing a simple
		// stack-based language
		StreamTokenizer st = new StreamTokenizer(new StringReader(
			splitConfig));
		st.whitespaceChars(0,' ');
		/* all printable ASCII characters */
		st.wordChars('#','~');
		st.commentChar('!');
		st.quoteChar('"');
		st.eolIsSignificant(false);

loop:		for(;;)
		{
			switch(st.nextToken())
			{
			case StreamTokenizer.TT_EOF:
				break loop;
			case StreamTokenizer.TT_WORD:
				if(st.sval.equals("vertical") ||
					st.sval.equals("horizontal"))
				{
					int orientation
						= (st.sval.equals("vertical")
						? JSplitPane.VERTICAL_SPLIT
						: JSplitPane.HORIZONTAL_SPLIT);
					int divider = ((Integer)stack.pop())
						.intValue();
					stack.push(splitPane = new JSplitPane(
						orientation,
						(Component)stack.pop(),
						(Component)stack.pop()));
					splitPane.setOneTouchExpandable(true);
					splitPane.setBorder(null);
					splitPane.setMinimumSize(
						new Dimension(0,0));
					splitPane.setDividerLocation(divider);
				}
				else if(st.sval.equals("buffer"))
				{
					Object obj = stack.pop();
					if(obj instanceof Integer)
					{
						int index = ((Integer)obj).intValue();
						if(index >= 0 && index < buffers.length)
							buffer = buffers[index];
					}
					else if(obj instanceof String)
					{
						String path = (String)obj;
						buffer = jEdit.getBuffer(path);
					}

					if(buffer == null)
						buffer = jEdit.getFirstBuffer();

					stack.push(editPane = createEditPane(
						buffer));
				}
				break;
			case StreamTokenizer.TT_NUMBER:
				stack.push(new Integer((int)st.nval));
				break;
			case '"':
				stack.push(st.sval);
				break;
			}
		}

		updateGutterBorders();

		return (Component)stack.peek();
	} //}}}

	//{{{ propertiesChanged() method
	/**
	 * Reloads various settings from the properties.
	 */
	private void propertiesChanged()
	{
		setJMenuBar(GUIUtilities.loadMenuBar("view.mbar"));

		loadToolBars();

		showFullPath = jEdit.getBooleanProperty("view.showFullPath");
		updateTitle();

		status.propertiesChanged();

		removeToolBar(status);
		getContentPane().remove(status);

		if(jEdit.getBooleanProperty("view.toolbar.alternateLayout"))
		{
			getContentPane().add(BorderLayout.NORTH,topToolBars);
			getContentPane().add(BorderLayout.SOUTH,bottomToolBars);
			if(!plainView && jEdit.getBooleanProperty("view.status.visible"))
				addToolBar(BOTTOM_GROUP,STATUS_BAR_LAYER,status);
		}
		else
		{
			dockableWindowManager.add(topToolBars,
				DockableLayout.TOP_TOOLBARS,0);
			dockableWindowManager.add(bottomToolBars,
				DockableLayout.BOTTOM_TOOLBARS,0);
			if(!plainView && jEdit.getBooleanProperty("view.status.visible"))
				getContentPane().add(BorderLayout.SOUTH,status);
		}

		getRootPane().revalidate();

		//SwingUtilities.updateComponentTreeUI(getRootPane());
	} //}}}

	//{{{ loadToolBars() method
	private void loadToolBars()
	{
		if(jEdit.getBooleanProperty("view.showToolbar") && !plainView)
		{
			if(toolBar != null)
				toolBarManager.removeToolBar(toolBar);

			toolBar = GUIUtilities.loadToolBar("view.toolbar");

			addToolBar(TOP_GROUP, SYSTEM_BAR_LAYER, toolBar);
		}
		else if(toolBar != null)
		{
			removeToolBar(toolBar);
			toolBar = null;
		}

		if(searchBar != null)
			removeToolBar(searchBar);

		if(jEdit.getBooleanProperty("view.showSearchbar") && !plainView)
		{
			if(searchBar == null)
				searchBar = new SearchBar(this,false);
			searchBar.propertiesChanged();
			addToolBar(TOP_GROUP,SEARCH_BAR_LAYER,searchBar);
		}
	} //}}}

	//{{{ createEditPane() method
	private EditPane createEditPane(Buffer buffer)
	{
		EditPane editPane = new EditPane(this,buffer);
		JEditTextArea textArea = editPane.getTextArea();
		textArea.addFocusListener(new FocusHandler());
		textArea.addCaretListener(new CaretHandler());
		textArea.addScrollListener(new ScrollHandler());
		EditBus.send(new EditPaneUpdate(editPane,EditPaneUpdate.CREATED));
		return editPane;
	} //}}}

	//{{{ setEditPane() method
	private void setEditPane(EditPane editPane)
	{
		this.editPane = editPane;
		status.updateCaretStatus();
		status.updateBufferStatus();
		status.updateMiscStatus();

		// repaint the gutter so that the border color
		// reflects the focus state
		updateGutterBorders();

		EditBus.send(new ViewUpdate(this,ViewUpdate.EDIT_PANE_CHANGED));
	} //}}}

	//{{{ handleBufferUpdate() method
	private void handleBufferUpdate(BufferUpdate msg)
	{
		Buffer buffer = msg.getBuffer();
		if(msg.getWhat() == BufferUpdate.DIRTY_CHANGED
			|| msg.getWhat() == BufferUpdate.LOADED)
		{
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				if(editPanes[i].getBuffer() == buffer)
				{
					updateTitle();
					break;
				}
			}
		}
	} //}}}

	//{{{ handleEditPaneUpdate() method
	private void handleEditPaneUpdate(EditPaneUpdate msg)
	{
		EditPane editPane = msg.getEditPane();
		if(editPane.getView() == this
			&& msg.getWhat() == EditPaneUpdate.BUFFER_CHANGED
			&& editPane.getBuffer().isLoaded())
		{
			status.updateCaretStatus();
			status.updateBufferStatus();
			status.updateMiscStatus();
		}
	} //}}}

	//{{{ updateGutterBorders() method
	/**
	 * Updates the borders of all gutters in this view to reflect the
	 * currently focused text area.
	 * @since jEdit 2.6final
	 */
	private void updateGutterBorders()
	{
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
			editPanes[i].getTextArea().getGutter().updateBorder();
	} //}}}

	//{{{ _preprocessKeyEvent() method
	private KeyEvent _preprocessKeyEvent(KeyEvent evt)
	{
		if(isClosed())
			return null;

		if (Debug.SIMPLIFIED_KEY_HANDLING)
		{
			/*
				It seems that the "else" path below does
				not work. Apparently, is is there to prevent
				some keyboard events to be "swallowed" by
				jEdit when the keyboard event in fact should
				be scheduled to swing for further handling.
				
				On some "key typed" events, the "return null;"
				is triggered. However, these key events
				actually do not seem to be handled elseewhere,
				so they are not handled at all.
				
				This behaviour exists with old keyboard handling
				as well as with new keyboard handling. However,
				the new keyboard handling is more sensitive
				about what kinds of key events it receives. It
				expects to see all "key typed" events,
				which is incompatible with the "return null;"
				below.
				
				This bug triggers jEdit bug 1493185 ( https://sourceforge.net/tracker/?func=detail&aid=1493185&group_id=588&atid=100588 ).
				
				Thus, we disable the possibility of
				key event swallowing for the new key event
				handling.			
				
			*/
		}
		else
		{		
			if(getFocusOwner() instanceof JComponent)
			{
				JComponent comp = (JComponent)getFocusOwner();
				InputMap map = comp.getInputMap();
				ActionMap am = comp.getActionMap();
	
				if(map != null && am != null && comp.isEnabled())
				{
					KeyStroke	keyStroke	= KeyStroke.getKeyStrokeForEvent(evt); 
					Object binding = map.get(keyStroke);
					if(binding != null && am.get(binding) != null)
					{
						return null;
					}
				}
			}
		}

		if(getFocusOwner() instanceof JTextComponent)
		{
			// fix for the bug where key events in JTextComponents
			// inside views are also handled by the input handler
			if(evt.getID() == KeyEvent.KEY_PRESSED)
			{
				switch(evt.getKeyCode())
				{
				case KeyEvent.VK_ENTER:
				case KeyEvent.VK_TAB:
				case KeyEvent.VK_BACK_SPACE:
				case KeyEvent.VK_SPACE:
					return null;
				}
			}
		}
		
		if(evt.isConsumed())
			return null;

		if(Debug.DUMP_KEY_EVENTS)
		{
			Log.log(Log.DEBUG,this,"Key event (preprocessing) : "
					+ GrabKeyDialog.toString(evt));
		}

		return KeyEventWorkaround.processKeyEvent(evt);
	} //}}}

	//}}}

	//{{{ Inner classes

	//{{{ CaretHandler class
	class CaretHandler implements CaretListener
	{
		public void caretUpdate(CaretEvent evt)
		{
			if(evt.getSource() == getTextArea())
				status.updateCaretStatus();
		}
	} //}}}

	//{{{ FocusHandler class
	class FocusHandler extends FocusAdapter
	{
		public void focusGained(FocusEvent evt)
		{
			// walk up hierarchy, looking for an EditPane
			Component comp = (Component)evt.getSource();
			while(!(comp instanceof EditPane))
			{
				if(comp == null)
					return;

				comp = comp.getParent();
			}

			if(comp != editPane)
				setEditPane((EditPane)comp);
			else
				updateGutterBorders();
		}
	} //}}}

	//{{{ ScrollHandler class
	class ScrollHandler implements ScrollListener
	{
		public void scrolledVertically(JEditTextArea textArea)
		{
			if(getTextArea() == textArea)
				status.updateCaretStatus();
		}

		public void scrolledHorizontally(JEditTextArea textArea) {}
	} //}}}

	//{{{ WindowHandler class
	class WindowHandler extends WindowAdapter
	{
		public void windowActivated(WindowEvent evt)
		{
			boolean editPaneChanged =
				(jEdit.getActiveView() != View.this);
			jEdit.setActiveView(View.this);

			// People have reported hangs with JDK 1.4; might be
			// caused by modal dialogs being displayed from
			// windowActivated()
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					jEdit.checkBufferStatus(View.this);
				}
			});

			if (editPaneChanged)
			{
				EditBus.send(new ViewUpdate(View.this,ViewUpdate
					.ACTIVATED));
			}
		}

		public void windowClosing(WindowEvent evt)
		{
			jEdit.closeView(View.this);
		}
	} //}}}

	//{{{ ViewConfig class
	public static class ViewConfig
	{
		public boolean plainView;
		public String splitConfig;
		public int x, y, width, height, extState;

		// dockables
		public String top, left, bottom, right;
		public int topPos, leftPos, bottomPos, rightPos;

		public ViewConfig()
		{
		}

		public ViewConfig(boolean plainView)
		{
			this.plainView = plainView;
			String prefix = (plainView ? "plain-view" : "view");
			x = jEdit.getIntegerProperty(prefix + ".x",0);
			y = jEdit.getIntegerProperty(prefix + ".y",0);
			width = jEdit.getIntegerProperty(prefix + ".width",0);
			height = jEdit.getIntegerProperty(prefix + ".height",0);
		}

		public ViewConfig(boolean plainView, String splitConfig,
			int x, int y, int width, int height, int extState)
		{
			this.plainView = plainView;
			this.splitConfig = splitConfig;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.extState = extState;
		}
	} //}}}

	//{{{ MyFocusTraversalPolicy class
	static class MyFocusTraversalPolicy extends LayoutFocusTraversalPolicy
	{
		public Component getDefaultComponent(Container focusCycleRoot)
		{
			return GUIUtilities.getView(focusCycleRoot).getTextArea();
		}
	} //}}}

	//}}}
}
