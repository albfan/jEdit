/*
 * EditPane.java - Text area and buffer switcher
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2000, 2005 Slava Pestov
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
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.lang.reflect.Method;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.jedit.options.GlobalOptions;
import org.gjt.sp.jedit.syntax.SyntaxStyle;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.util.Log;
//}}}

/**
 * A panel containing a text area.<p>
 *
 * In a BeanShell script, you can obtain the current edit pane from the
 * <code>editPane</code> variable.<p>
 *
 * This class does not have a public constructor.
 * Edit panes can be created and destroyed using methods in the
 * {@link View} class.<p>
 *
 * Each edit pane can edit one buffer at a time.
 *
 * @see View#splitHorizontally()
 * @see View#splitVertically()
 * @see View#unsplitCurrent()
 * @see View#unsplit()
 * @see View#getEditPane()
 * @see View#getEditPanes()
 *
 * @author Slava Pestov
 * @version $Id$
 */
public class EditPane extends JPanel implements EBComponent
{
	//{{{ getView() method
	/**
	 * Returns the view containing this edit pane.
	 * @since jEdit 2.5pre2
	 */
	public View getView()
	{
		return view;
	} //}}}

	//{{{ getBuffer() method
	/**
	 * Returns the current buffer.
	 * @since jEdit 2.5pre2
	 */
	public Buffer getBuffer()
	{
		return buffer;
	} //}}}

	//{{{ setBuffer() method
	/**
	 * Sets the current buffer.
	 * @param buffer The buffer to edit.
	 * @since jEdit 2.5pre2
	 */
	public void setBuffer(final Buffer buffer)
	{
		if(buffer == null)
			throw new NullPointerException();

		if(this.buffer == buffer)
			return;

		//if(buffer.insideCompoundEdit())
		//	buffer.endCompoundEdit();

		recentBuffer = this.buffer;
		if(recentBuffer != null)
			saveCaretInfo();
		this.buffer = buffer;

		textArea.setBuffer(buffer);

		if(!init)
		{
			view.updateTitle();

			if(bufferSwitcher != null)
			{
				if(bufferSwitcher.getSelectedItem() != buffer)
					bufferSwitcher.setSelectedItem(buffer);
			}

			EditBus.send(new EditPaneUpdate(this,EditPaneUpdate
				.BUFFER_CHANGED));
		}

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				// only do this if we are the current edit pane
				if(view.getEditPane() == EditPane.this
					&& (bufferSwitcher == null
					|| !bufferSwitcher.isPopupVisible()))
				{
					textArea.requestFocus();
				}
			}
		});

		// Only do this after all I/O requests are complete
		Runnable runnable = new Runnable()
		{
			public void run()
			{
				// avoid a race condition
				// see bug #834338
				if(buffer == getBuffer())
					loadCaretInfo();
			}
		};

		if(buffer.isPerformingIO())
			VFSManager.runInAWTThread(runnable);
		else
			runnable.run();
	} //}}}

	//{{{ prevBuffer() method
	/**
	 * Selects the previous buffer.
	 * @since jEdit 2.7pre2
	 */
	public void prevBuffer()
	{
		Buffer buffer = this.buffer.getPrev();
		if(buffer == null)
			setBuffer(jEdit.getLastBuffer());
		else
			setBuffer(buffer);
	} //}}}

	//{{{ nextBuffer() method
	/**
	 * Selects the next buffer.
	 * @since jEdit 2.7pre2
	 */
	public void nextBuffer()
	{
		Buffer buffer = this.buffer.getNext();
		if(buffer == null)
			setBuffer(jEdit.getFirstBuffer());
		else
			setBuffer(buffer);
	} //}}}

	//{{{ recentBuffer() method
	/**
	 * Selects the most recently edited buffer.
	 * @since jEdit 2.7pre2
	 */
	public void recentBuffer()
	{
		if(recentBuffer != null)
			setBuffer(recentBuffer);
		else
			getToolkit().beep();
	} //}}}

	//{{{ focusOnTextArea() method
	/**
	 * Sets the focus onto the text area.
	 * @since jEdit 2.5pre2
	 */
	public void focusOnTextArea()
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				textArea.requestFocus();
			}
		});
	} //}}}

	//{{{ getTextArea() method
	/**
	 * Returns the view's text area.
	 * @since jEdit 2.5pre2
	 */
	public JEditTextArea getTextArea()
	{
		return textArea;
	} //}}}

	//{{{ getBufferSwitcher() method
	/**
	 * Returns the buffer switcher combo box instance.
	 * @since jEdit 4.1pre8
	 */
	public BufferSwitcher getBufferSwitcher()
	{
		return bufferSwitcher;
	} //}}}

	//{{{ showBufferSwitcher() method
	/**
	 * Shows the buffer switcher combo box.
	 * @since jEdit 4.1pre8
	 */
	public void showBufferSwitcher()
	{
		if(bufferSwitcher == null)
			getToolkit().beep();
		else
		{
			bufferSwitcher.requestFocus();
			bufferSwitcher.showPopup();
		}
	} //}}}

	//{{{ saveCaretInfo() method
	/**
	 * Saves the caret information to the current buffer.
	 * @since jEdit 2.5pre2
	 */
	public void saveCaretInfo()
	{
		if(!buffer.isLoaded())
			return;

		buffer.setIntegerProperty(Buffer.CARET,
			textArea.getCaretPosition());

		/*Selection[] selection = textArea.getSelection();
		if(selection != null)
			buffer.setProperty(Buffer.SELECTION,selection);*/

		buffer.setIntegerProperty(Buffer.SCROLL_VERT,
			textArea.getFirstPhysicalLine());
		buffer.setIntegerProperty(Buffer.SCROLL_HORIZ,
			textArea.getHorizontalOffset());
	} //}}}

	//{{{ loadCaretInfo() method
	/**
	 * Loads the caret information from the current buffer.
	 * @since jEdit 2.5pre2
	 */
	public void loadCaretInfo()
	{
		Integer caret = (Integer)buffer.getProperty(Buffer.CARET);
		//Selection[] selection = (Selection[])buffer.getProperty(Buffer.SELECTION);

		Integer firstLine = (Integer)buffer.getProperty(Buffer.SCROLL_VERT);
		Integer horizontalOffset = (Integer)buffer.getProperty(Buffer.SCROLL_HORIZ);

		if(caret != null)
		{
			textArea.setCaretPosition(Math.min(caret.intValue(),
				buffer.getLength()));
		}

		/*if(selection != null)
			textArea.setSelection(selection);*/

		if(firstLine != null)
			textArea.setFirstPhysicalLine(firstLine.intValue());

		if(horizontalOffset != null)
			textArea.setHorizontalOffset(horizontalOffset.intValue());

		/* Silly bug workaround #8694. If you look at the above code,
		 * note that we restore the saved caret position first, then
		 * scroll to the saved location. However, the caret changing
		 * can itself result in scrolling to a different location than
		 * what was saved; and since moveCaretPosition() calls
		 * updateBracketHighlight(), the bracket highlight's out of
		 * bounds calculation will rely on a different set of physical
		 * first/last lines than what we will end up with eventually.
		 * Instead of confusing the user with status messages that
		 * appear at random when switching buffers, we simply hide the
		 * message altogether. */
		view.getStatus().setMessage(null);
	} //}}}

	//{{{ handleMessage() method
	public void handleMessage(EBMessage msg)
	{
		if(msg instanceof PropertiesChanged)
		{
			propertiesChanged();
			loadBufferSwitcher();
		}
		else if(msg instanceof BufferUpdate)
			handleBufferUpdate((BufferUpdate)msg);
	} //}}}

	//{{{ getMinimumSize() method
	/**
	 * Returns 0,0 for split pane compatibility.
	 */
	public final Dimension getMinimumSize()
	{
		return new Dimension(0,0);
	} //}}}

	//{{{ toString() method
	public String toString()
	{
		return getClass().getName() + "["
			+ (view.getEditPane() == this
			? "active" : "inactive")
			+ "]";
	} //}}}

	//{{{ Package-private members

	//{{{ EditPane constructor
	EditPane(View view, Buffer buffer)
	{
		super(new BorderLayout());

		init = true;

		this.view = view;

		EditBus.addToBus(this);

		textArea = new JEditTextArea(view);
		textArea.addStatusListener(new StatusHandler());

		add(BorderLayout.CENTER,textArea);

		propertiesChanged();

		if(buffer == null)
			setBuffer(jEdit.getFirstBuffer());
		else
			setBuffer(buffer);

		loadBufferSwitcher();

		init = false;
	} //}}}

	//{{{ close() method
	void close()
	{
		saveCaretInfo();
		EditBus.send(new EditPaneUpdate(this,EditPaneUpdate.DESTROYED));
		EditBus.removeFromBus(this);
		textArea.dispose();
	} //}}}

	//}}}

	//{{{ Private members

	//{{{ Instance variables
	private boolean init;
	private View view;
	private Buffer buffer;
	private Buffer recentBuffer;
	private BufferSwitcher bufferSwitcher;
	private JEditTextArea textArea;
	//}}}

	//{{{ propertiesChanged() method
	private void propertiesChanged()
	{
		TextAreaPainter painter = textArea.getPainter();

		painter.setFont(jEdit.getFontProperty("view.font"));
		painter.setStructureHighlightEnabled(jEdit.getBooleanProperty(
			"view.structureHighlight"));
		painter.setStructureHighlightColor(
			jEdit.getColorProperty("view.structureHighlightColor"));
		painter.setEOLMarkersPainted(jEdit.getBooleanProperty(
			"view.eolMarkers"));
		painter.setEOLMarkerColor(
			jEdit.getColorProperty("view.eolMarkerColor"));
		painter.setWrapGuidePainted(jEdit.getBooleanProperty(
			"view.wrapGuide"));
		painter.setWrapGuideColor(
			jEdit.getColorProperty("view.wrapGuideColor"));
		painter.setCaretColor(
			jEdit.getColorProperty("view.caretColor"));
		painter.setSelectionColor(
			jEdit.getColorProperty("view.selectionColor"));
		painter.setMultipleSelectionColor(
			jEdit.getColorProperty("view.multipleSelectionColor"));
		painter.setBackground(
			jEdit.getColorProperty("view.bgColor"));
		painter.setForeground(
			jEdit.getColorProperty("view.fgColor"));
		painter.setBlockCaretEnabled(jEdit.getBooleanProperty(
			"view.blockCaret"));
		painter.setLineHighlightEnabled(jEdit.getBooleanProperty(
			"view.lineHighlight"));
		painter.setLineHighlightColor(
			jEdit.getColorProperty("view.lineHighlightColor"));
		painter.setAntiAliasEnabled(jEdit.getBooleanProperty(
			"view.antiAlias"));
		painter.setFractionalFontMetricsEnabled(jEdit.getBooleanProperty(
			"view.fracFontMetrics"));

		String defaultFont = jEdit.getProperty("view.font");
		int defaultFontSize = jEdit.getIntegerProperty("view.fontsize",12);
		painter.setStyles(GUIUtilities.loadStyles(defaultFont,defaultFontSize));

		SyntaxStyle[] foldLineStyle = new SyntaxStyle[4];
		for(int i = 0; i <= 3; i++)
		{
			foldLineStyle[i] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.foldLine." + i),
				defaultFont,defaultFontSize);
		}
		painter.setFoldLineStyle(foldLineStyle);
		Gutter gutter = textArea.getGutter();
		gutter.setExpanded(jEdit.getBooleanProperty(
			"view.gutter.lineNumbers"));
		int interval = jEdit.getIntegerProperty(
			"view.gutter.highlightInterval",5);
		gutter.setHighlightInterval(interval);
		gutter.setCurrentLineHighlightEnabled(jEdit.getBooleanProperty(
			"view.gutter.highlightCurrentLine"));
		gutter.setStructureHighlightEnabled(jEdit.getBooleanProperty(
			"view.gutter.structureHighlight"));
		gutter.setStructureHighlightColor(
			jEdit.getColorProperty("view.gutter.structureHighlightColor"));
		gutter.setBackground(
			jEdit.getColorProperty("view.gutter.bgColor"));
		gutter.setForeground(
			jEdit.getColorProperty("view.gutter.fgColor"));
		gutter.setHighlightedForeground(
			jEdit.getColorProperty("view.gutter.highlightColor"));
		gutter.setFoldColor(
			jEdit.getColorProperty("view.gutter.foldColor"));
		gutter.setMarkerHighlightColor(
			jEdit.getColorProperty("view.gutter.markerColor"));
		gutter.setMarkerHighlightEnabled(jEdit.getBooleanProperty(
			"view.gutter.markerHighlight"));
		gutter.setCurrentLineForeground(
			jEdit.getColorProperty("view.gutter.currentLineColor"));
		String alignment = jEdit.getProperty(
			"view.gutter.numberAlignment");
		if ("right".equals(alignment))
		{
			gutter.setLineNumberAlignment(Gutter.RIGHT);
		}
		else if ("center".equals(alignment))
		{
			gutter.setLineNumberAlignment(Gutter.CENTER);
		}
		else // left == default case
		{
			gutter.setLineNumberAlignment(Gutter.LEFT);
		}

		gutter.setFont(jEdit.getFontProperty("view.gutter.font"));

		int width = jEdit.getIntegerProperty(
			"view.gutter.borderWidth",3);
		gutter.setBorder(width,
			jEdit.getColorProperty("view.gutter.focusBorderColor"),
			jEdit.getColorProperty("view.gutter.noFocusBorderColor"),
			textArea.getPainter().getBackground());

		textArea.setCaretBlinkEnabled(jEdit.getBooleanProperty(
			"view.caretBlink"));

		textArea.setElectricScroll(jEdit.getIntegerProperty(
			"view.electricBorders",0));

		// Set up the right-click popup menu
		JPopupMenu popup = GUIUtilities.loadPopupMenu("view.context");
		JMenuItem customize = new JMenuItem(jEdit.getProperty(
			"view.context.customize"));
		customize.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt)
			{
				new GlobalOptions(view,"context");
			}
		});
		popup.addSeparator();
		popup.add(customize);
		textArea.setRightClickPopup(popup);

		// use old property name for backwards compatibility
		textArea.setQuickCopyEnabled(jEdit.getBooleanProperty(
			"view.middleMousePaste"));

		textArea.setDragEnabled(jEdit.getBooleanProperty(
			"view.dragAndDrop"));

		textArea.setJoinNonWordChars(jEdit.getBooleanProperty(
			"view.joinNonWordChars"));

		textArea.propertiesChanged();
	} //}}}

	//{{{ loadBufferSwitcher() method
	private void loadBufferSwitcher()
	{
		if(jEdit.getBooleanProperty("view.showBufferSwitcher"))
		{
			if(bufferSwitcher == null)
			{
				bufferSwitcher = new BufferSwitcher(this);
				add(BorderLayout.NORTH,bufferSwitcher);
				bufferSwitcher.updateBufferList();
				revalidate();
			}
		}
		else if(bufferSwitcher != null)
		{
			remove(bufferSwitcher);
			revalidate();
			bufferSwitcher = null;
		}
	} //}}}

	//{{{ handleBufferUpdate() method
	private void handleBufferUpdate(BufferUpdate msg)
	{
		Buffer _buffer = msg.getBuffer();
		if(msg.getWhat() == BufferUpdate.CREATED)
		{
			if(bufferSwitcher != null)
				bufferSwitcher.updateBufferList();

			/* When closing the last buffer, the BufferUpdate.CLOSED
			 * handler doesn't call setBuffer(), because null buffers
			 * are not supported. Instead, it waits for the subsequent
			 * 'Untitled' file creation. */
			if(buffer.isClosed())
			{
				setBuffer(jEdit.getFirstBuffer());
				// since recentBuffer will be set to the one that
				// was closed
				recentBuffer = null;
			}
		}
		else if(msg.getWhat() == BufferUpdate.CLOSED)
		{
			if(bufferSwitcher != null)
				bufferSwitcher.updateBufferList();

			if(_buffer == buffer)
			{
				Buffer newBuffer = (recentBuffer != null ?
					recentBuffer : _buffer.getPrev());
				if(newBuffer != null && !newBuffer.isClosed())
				{
					setBuffer(newBuffer);
					recentBuffer = newBuffer.getPrev();
				}
				else if(jEdit.getBufferCount() != 0)
				{
					setBuffer(jEdit.getFirstBuffer());
					recentBuffer = null;
				}
			}
			else if(_buffer == recentBuffer)
				recentBuffer = null;
		}
		else if(msg.getWhat() == BufferUpdate.LOAD_STARTED)
		{
			if(_buffer == buffer)
			{
				textArea.setCaretPosition(0);
				textArea.getPainter().repaint();
			}
		}
		else if(msg.getWhat() == BufferUpdate.LOADED)
		{
			if(_buffer == buffer)
			{
				textArea.repaint();
				if(bufferSwitcher != null)
					bufferSwitcher.updateBufferList();

				if(view.getEditPane() == this)
				{
					StatusBar status = view.getStatus();
					status.updateCaretStatus();
					status.updateBufferStatus();
					status.updateMiscStatus();
				}

				loadCaretInfo();
			}

		}
		else if(msg.getWhat() == BufferUpdate.DIRTY_CHANGED)
		{
			if(_buffer == buffer)
			{
				if(bufferSwitcher != null)
				{
					if(buffer.isDirty())
						bufferSwitcher.repaint();
					else
						bufferSwitcher.updateBufferList();
				}
			}
		}
		else if(msg.getWhat() == BufferUpdate.MARKERS_CHANGED)
		{
			if(_buffer == buffer)
				textArea.getGutter().repaint();
		}
		else if(msg.getWhat() == BufferUpdate.PROPERTIES_CHANGED)
		{
			if(_buffer == buffer && buffer.isLoaded())
			{
				textArea.propertiesChanged();
				if(view.getEditPane() == this)
					view.getStatus().updateBufferStatus();
			}
		}
		else if(msg.getWhat() == BufferUpdate.SAVED)
		{
			if(_buffer == buffer)
				textArea.propertiesChanged();
		}
	} //}}}

	//}}}

	//{{{ StatusHandler class
	class StatusHandler implements StatusListener
	{
		public void statusChanged(JEditTextArea textArea, int flag, boolean value)
		{
			StatusBar status = view.getStatus();
			if(status == null)
				return;
			
			switch(flag)
			{
			case OVERWRITE_CHANGED:
				status.setMessageAndClear(
					jEdit.getProperty("view.status.overwrite-changed",
					new Integer[] { new Integer(value ? 1 : 0) }));
				break;
			case MULTI_SELECT_CHANGED:
				status.setMessageAndClear(
					jEdit.getProperty("view.status.multi-changed",
					new Integer[] { new Integer(value ? 1 : 0) }));
				break;
			case RECT_SELECT_CHANGED:
				status.setMessageAndClear(
					jEdit.getProperty("view.status.rect-select-changed",
					new Integer[] { new Integer(value ? 1 : 0) }));
				break;
			}
			
			status.updateMiscStatus();
		}
	
		public void bracketSelected(JEditTextArea textArea, int line, String text)
		{
			StatusBar status = view.getStatus();
			if(status == null)
				return;
			
			status.setMessageAndClear(jEdit.getProperty(
				"view.status.bracket",new Object[] {
				new Integer(line), text }));
		}
	
		public void narrowActive(JEditTextArea textArea)
		{
			StatusBar status = view.getStatus();
			if(status == null)
				return;
			
			status.setMessageAndClear(
				jEdit.getProperty("view.status.narrow"));
		}
	} //}}}
}
