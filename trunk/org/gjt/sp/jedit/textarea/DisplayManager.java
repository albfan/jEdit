/*
 * DisplayManager.java - Low-level text display
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2001, 2005 Slava Pestov
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

package org.gjt.sp.jedit.textarea;

//{{{ Imports
import java.awt.Toolkit;
import java.util.*;
import org.gjt.sp.jedit.buffer.*;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.Debug;
import org.gjt.sp.util.Log;
//}}}

/**
 * Manages low-level text display tasks.
 * @since jEdit 4.2pre1
 * @author Slava Pestov
 * @version $Id$
 */
public class DisplayManager
{
	//{{{ Static part

	//{{{ getDisplayManager() method
	static DisplayManager getDisplayManager(Buffer buffer,
		JEditTextArea textArea)
	{
		List l = (List)bufferMap.get(buffer);
		DisplayManager dmgr;
		if(l == null)
		{
			l = new LinkedList();
			bufferMap.put(buffer,l);
		}

		/* An existing display manager's fold visibility map
		that a new display manager will inherit */
		DisplayManager copy = null;
		Iterator liter = l.iterator();
		while(liter.hasNext())
		{
			dmgr = (DisplayManager)liter.next();
			copy = dmgr;
			if(!dmgr.inUse && dmgr.textArea == textArea)
			{
				dmgr.inUse = true;
				return dmgr;
			}
		}

		// if we got here, no unused display manager in list
		dmgr = new DisplayManager(buffer,textArea,copy);
		dmgr.inUse = true;
		l.add(dmgr);

		return dmgr;
	} //}}}

	//{{{ releaseDisplayManager() method
	static void releaseDisplayManager(DisplayManager dmgr)
	{
		dmgr.inUse = false;
	} //}}}

	//{{{ bufferClosed() method
	public static void bufferClosed(Buffer buffer)
	{
		bufferMap.remove(buffer);
	} //}}}

	//{{{ textAreaDisposed() method
	static void textAreaDisposed(JEditTextArea textArea)
	{
		Iterator biter = bufferMap.values().iterator();
		while(biter.hasNext())
		{
			List l = (List)biter.next();
			Iterator liter = l.iterator();
			while(liter.hasNext())
			{
				DisplayManager dmgr = (DisplayManager)
					liter.next();
				if(dmgr.textArea == textArea)
				{
					dmgr.dispose();
					liter.remove();
				}
			}
		}
	} //}}}

	private static Map bufferMap = new HashMap();
	//}}}

	//{{{ getBuffer() method
	/**
	 * @since jEdit 4.3pre2
	 */
	public Buffer getBuffer()
	{
		return buffer;
	} //}}}

	//{{{ isLineVisible() method
	/**
	 * Returns if the specified line is visible.
	 * @param line A physical line index
	 * @since jEdit 4.2pre1
	 */
	public final boolean isLineVisible(int line)
	{
		return folds.search(line) % 2 == 0;
	} //}}}

	//{{{ getFirstVisibleLine() method
	/**
	 * Returns the physical line number of the first visible line.
	 * @since jEdit 4.2pre1
	 */
	public int getFirstVisibleLine()
	{
		return folds.first();
	} //}}}

	//{{{ getLastVisibleLine() method
	/**
	 * Returns the physical line number of the last visible line.
	 * @since jEdit 4.2pre1
	 */
	public int getLastVisibleLine()
	{
		return folds.last();
	} //}}}

	//{{{ getNextVisibleLine() method
	/**
	 * Returns the next visible line after the specified line index.
	 * @param line A physical line index
	 * @since jEdit 4.0pre1
	 */
	public int getNextVisibleLine(int line)
	{
		if(line < 0 || line >= buffer.getLineCount())
			throw new ArrayIndexOutOfBoundsException(line);

		return folds.next(line);
	} //}}}

	//{{{ getPrevVisibleLine() method
	/**
	 * Returns the previous visible line before the specified line index.
	 * @param line A physical line index
	 * @since jEdit 4.0pre1
	 */
	public int getPrevVisibleLine(int line)
	{
		if(line < 0 || line >= buffer.getLineCount())
			throw new ArrayIndexOutOfBoundsException(line);

		return folds.prev(line);
	} //}}}

	//{{{ getScreenLineCount() method
	public final int getScreenLineCount(int line)
	{
		if(!screenLineMgr.isScreenLineCountValid(line))
			throw new RuntimeException("Invalid screen line count: " + line);

		return screenLineMgr.getScreenLineCount(line);
	} //}}}

	//{{{ getScrollLineCount() method
	public final int getScrollLineCount()
	{
		return scrollLineCount.scrollLine;
	} //}}}

	//{{{ collapseFold() method
	/**
	 * Collapses the fold at the specified physical line index.
	 * @param line A physical line index
	 * @since jEdit 4.2pre1
	 */
	public void collapseFold(int line)
	{
		int lineCount = buffer.getLineCount();
		int start = 0;
		int end = lineCount - 1;

		// if the caret is on a collapsed fold, collapse the
		// parent fold
		if(line != 0
			&& line != buffer.getLineCount() - 1
			&& buffer.isFoldStart(line)
			&& !isLineVisible(line + 1))
		{
			line--;
		}

		int initialFoldLevel = buffer.getFoldLevel(line);

		//{{{ Find fold start and end...
		if(line != lineCount - 1
			&& buffer.getFoldLevel(line + 1) > initialFoldLevel)
		{
			// this line is the start of a fold
			start = line + 1;

			for(int i = line + 1; i < lineCount; i++)
			{
				if(buffer.getFoldLevel(i) <= initialFoldLevel)
				{
					end = i - 1;
					break;
				}
			}
		}
		else
		{
			boolean ok = false;

			// scan backwards looking for the start
			for(int i = line - 1; i >= 0; i--)
			{
				if(buffer.getFoldLevel(i) < initialFoldLevel)
				{
					start = i + 1;
					ok = true;
					break;
				}
			}

			if(!ok)
			{
				// no folds in buffer
				return;
			}

			for(int i = line + 1; i < lineCount; i++)
			{
				if(buffer.getFoldLevel(i) < initialFoldLevel)
				{
					end = i - 1;
					break;
				}
			}
		} //}}}

		// Collapse the fold...
		hideLineRange(start,end);

		notifyScreenLineChanges();
		textArea.foldStructureChanged();
	} //}}}

	//{{{ expandFold() method
	/**
	 * Expands the fold at the specified physical line index.
	 * @param line A physical line index
	 * @param fully If true, all subfolds will also be expanded
	 * @since jEdit 4.2pre1
	 */
	public int expandFold(int line, boolean fully)
	{
		// the first sub-fold. used by JEditTextArea.expandFold().
		int returnValue = -1;

		int lineCount = buffer.getLineCount();
		int start = 0;
		int end = lineCount - 1;

		int initialFoldLevel = buffer.getFoldLevel(line);

		//{{{ Find fold start and fold end...
		if(line != lineCount - 1
			&& isLineVisible(line)
			&& !isLineVisible(line + 1)
			&& buffer.getFoldLevel(line + 1) > initialFoldLevel)
		{
			// this line is the start of a fold

			int index = folds.search(line + 1);
			if(index == -1)
			{
				expandAllFolds();
				return -1;
			}

			start = folds.lookup(index);
			if(index != folds.count() - 1)
				end = folds.lookup(index + 1) - 1;
			else
			{
				start = line + 1;

				for(int i = line + 1; i < lineCount; i++)
				{
					if(/* isLineVisible(i) && */
						buffer.getFoldLevel(i) <= initialFoldLevel)
					{
						end = i - 1;
						break;
					}
				}
			}
		}
		else
		{
			int index = folds.search(line);
			if(index == -1)
			{
				expandAllFolds();
				return -1;
			}

			start = folds.lookup(index);
			if(index != folds.count() - 1)
				end = folds.lookup(index + 1) - 1;
			else
			{
				for(int i = line + 1; i < lineCount; i++)
				{
					//XXX
					if((isLineVisible(i) &&
						buffer.getFoldLevel(i) < initialFoldLevel)
						|| i == getLastVisibleLine())
					{
						end = i - 1;
						break;
					}
				}
			}
		} //}}}

		//{{{ Expand the fold...
		if(fully)
		{
			showLineRange(start,end);
		}
		else
		{
			// we need a different value of initialFoldLevel here!
			initialFoldLevel = buffer.getFoldLevel(start);

			int firstVisible = start;

			for(int i = start; i <= end; i++)
			{
				if(buffer.getFoldLevel(i) > initialFoldLevel)
				{
					if(returnValue == -1
						&& i != 0
						&& buffer.isFoldStart(i - 1))
					{
						returnValue = i - 1;
					}

					if(firstVisible != i)
					{
						showLineRange(firstVisible,i - 1);
					}
					firstVisible = i + 1;
				}
			}

			if(firstVisible == end + 1)
				returnValue = -1;
			else
				showLineRange(firstVisible,end);

			if(!isLineVisible(line))
			{
				// this is a hack, and really needs to be done better.
				expandFold(line,false);
				return returnValue;
			}
		} //}}}

		notifyScreenLineChanges();
		textArea.foldStructureChanged();

		return returnValue;
	} //}}}

	//{{{ expandAllFolds() method
	/**
	 * Expands all folds.
	 * @since jEdit 4.2pre1
	 */
	public void expandAllFolds()
	{
		showLineRange(0,buffer.getLineCount() - 1);
		notifyScreenLineChanges();
		textArea.foldStructureChanged();
	} //}}}

	//{{{ expandFolds() method
	/**
	 * This method should only be called from <code>actions.xml</code>.
	 * @since jEdit 4.2pre1
	 */
	public void expandFolds(char digit)
	{
		if(digit < '1' || digit > '9')
		{
			Toolkit.getDefaultToolkit().beep();
			return;
		}
		else
			expandFolds((int)(digit - '1') + 1);
	} //}}}

	//{{{ expandFolds() method
	/**
	 * Expands all folds with the specified fold level.
	 * @param foldLevel The fold level
	 * @since jEdit 4.2pre1
	 */
	public void expandFolds(int foldLevel)
	{
		if(buffer.getFoldHandler() instanceof IndentFoldHandler)
			foldLevel = (foldLevel - 1) * buffer.getIndentSize() + 1;

		showLineRange(0,buffer.getLineCount() - 1);

		/* this ensures that the first line is always visible */
		boolean seenVisibleLine = false;

		int firstInvisible = 0;

		for(int i = 0; i < buffer.getLineCount(); i++)
		{
			if(!seenVisibleLine || buffer.getFoldLevel(i) < foldLevel)
			{
				if(firstInvisible != i)
				{
					hideLineRange(firstInvisible,
						i - 1);
				}
				firstInvisible = i + 1;
				seenVisibleLine = true;
			}
		}

		if(firstInvisible != buffer.getLineCount())
			hideLineRange(firstInvisible,buffer.getLineCount() - 1);

		notifyScreenLineChanges();
		textArea.foldStructureChanged();
	} //}}}

	//{{{ narrow() method
	/**
	 * Narrows the visible portion of the buffer to the specified
	 * line range.
	 * @param start The first line
	 * @param end The last line
	 * @since jEdit 4.2pre1
	 */
	public void narrow(int start, int end)
	{
		if(start > end || start < 0 || end >= buffer.getLineCount())
			throw new ArrayIndexOutOfBoundsException(start + ", " + end);

		if(start < getFirstVisibleLine() || end > getLastVisibleLine())
			expandAllFolds();

		if(start != 0)
			hideLineRange(0,start - 1);
		if(end != buffer.getLineCount() - 1)
			hideLineRange(end + 1,buffer.getLineCount() - 1);

		// if we narrowed to a single collapsed fold
		if(start != buffer.getLineCount() - 1
			&& !isLineVisible(start + 1))
			expandFold(start,false);

		// Hack... need a more direct way of obtaining a view?
		// JEditTextArea.getView() method?
		textArea.fireNarrowActive();

		notifyScreenLineChanges();
		textArea.foldStructureChanged();
	} //}}}

	//{{{ Package-private members
	FirstLine firstLine;
	ScrollLineCount scrollLineCount;
	ScreenLineManager screenLineMgr;
	RangeMap folds;

	//{{{ init() method
	void init()
	{
		if(initialized)
		{
			if(buffer.isLoaded())
			{
				resetAnchors();

				textArea.updateScrollBar();
				textArea.recalculateLastPhysicalLine();
			}
		}
		else
		{
			initialized = true;
			folds = new RangeMap();
			if(buffer.isLoaded())
				bufferChangeHandler.foldHandlerChanged(buffer);
			else
				folds.reset(buffer.getLineCount());
			notifyScreenLineChanges();
		}
	} //}}}

	//{{{ notifyScreenLineChanges() method
	void notifyScreenLineChanges()
	{
		if(Debug.SCROLL_DEBUG)
			Log.log(Log.DEBUG,this,"notifyScreenLineChanges()");

		// when the text area switches to us, it will do
		// a reset anyway
		if(textArea.getDisplayManager() != this)
			return;

		try
		{
			if(firstLine.callReset)
				firstLine.reset();
			else if(firstLine.callChanged)
			{
				firstLine.changed();

				if(!scrollLineCount.callChanged
					&& !scrollLineCount.callReset)
				{
					textArea.updateScrollBar();
					textArea.recalculateLastPhysicalLine();
				}
				else
				{
					// ScrollLineCount.changed() does the same
					// thing
				}
			}

			if(scrollLineCount.callReset)
			{
				scrollLineCount.reset();
				firstLine.ensurePhysicalLineIsVisible();

				textArea.recalculateLastPhysicalLine();
				textArea.updateScrollBar();
			}
			else if(scrollLineCount.callChanged)
			{
				scrollLineCount.changed();
				textArea.updateScrollBar();
				textArea.recalculateLastPhysicalLine();
			}
		}
		finally
		{
			firstLine.callReset = firstLine.callChanged = false;
			scrollLineCount.callReset = scrollLineCount.callChanged = false;
		}
	} //}}}

	//{{{ setFirstLine() method
	void setFirstLine(int oldFirstLine, int firstLine)
	{
		int visibleLines = textArea.getVisibleLines();

		if(firstLine >= oldFirstLine + visibleLines)
		{
			this.firstLine.scrollDown(firstLine - oldFirstLine);
			textArea.chunkCache.invalidateAll();
		}
		else if(firstLine <= oldFirstLine - visibleLines)
		{
			this.firstLine.scrollUp(oldFirstLine - firstLine);
			textArea.chunkCache.invalidateAll();
		}
		else if(firstLine > oldFirstLine)
		{
			this.firstLine.scrollDown(firstLine - oldFirstLine);
			textArea.chunkCache.scrollDown(firstLine - oldFirstLine);
		}
		else if(firstLine < oldFirstLine)
		{
			this.firstLine.scrollUp(oldFirstLine - firstLine);
			textArea.chunkCache.scrollUp(oldFirstLine - firstLine);
		}

		notifyScreenLineChanges();
	} //}}}

	//{{{ setFirstPhysicalLine() method
	void setFirstPhysicalLine(int amount, int skew)
	{
		int oldFirstLine = textArea.getFirstLine();

		if(amount == 0)
		{
			skew -= this.firstLine.skew;

			// JEditTextArea.scrollTo() needs this to simplify
			// its code
			if(skew < 0)
				this.firstLine.scrollUp(-skew);
			else if(skew > 0)
				this.firstLine.scrollDown(skew);
			else
			{
				// nothing to do
				return;
			}
		}
		else if(amount > 0)
			this.firstLine.physDown(amount,skew);
		else if(amount < 0)
			this.firstLine.physUp(-amount,skew);

		int firstLine = textArea.getFirstLine();
		int visibleLines = textArea.getVisibleLines();

		if(firstLine == oldFirstLine)
			/* do nothing */;
		else if(firstLine >= oldFirstLine + visibleLines
			|| firstLine <= oldFirstLine - visibleLines)
		{
			textArea.chunkCache.invalidateAll();
		}
		else if(firstLine > oldFirstLine)
		{
			textArea.chunkCache.scrollDown(firstLine - oldFirstLine);
		}
		else if(firstLine < oldFirstLine)
		{
			textArea.chunkCache.scrollUp(oldFirstLine - firstLine);
		}

		// we have to be careful
		notifyScreenLineChanges();
	} //}}}

	//{{{ invalidateScreenLineCounts() method
	void invalidateScreenLineCounts()
	{
		screenLineMgr.invalidateScreenLineCounts();
		firstLine.callReset = true;
		scrollLineCount.callReset = true;
	} //}}}

	//{{{ updateScreenLineCount() method
	void updateScreenLineCount(int line)
	{
		if(!screenLineMgr.isScreenLineCountValid(line))
		{
			int newCount = textArea.chunkCache
				.getLineSubregionCount(line);

			setScreenLineCount(line,newCount);
		}
	} //}}}

	//{{{ bufferLoaded() method
	void bufferLoaded()
	{
		folds.reset(buffer.getLineCount());
		screenLineMgr.reset();

		if(textArea.getDisplayManager() == this)
		{
			textArea.propertiesChanged();
			init();
		}

		int collapseFolds = buffer.getIntegerProperty(
			"collapseFolds",0);
		if(collapseFolds != 0)
			expandFolds(collapseFolds);
	} //}}}

	//{{{ foldHandlerChanged() method
	void foldHandlerChanged()
	{
		if(!buffer.isLoaded())
			return;

		folds.reset(buffer.getLineCount());
		resetAnchors();

		int collapseFolds = buffer.getIntegerProperty(
			"collapseFolds",0);
		if(collapseFolds != 0)
			expandFolds(collapseFolds);
	} //}}}

	//}}}

	//{{{ Private members
	private boolean initialized;
	private boolean inUse;
	private Buffer buffer;
	private JEditTextArea textArea;
	private BufferChangeHandler bufferChangeHandler;

	//{{{ DisplayManager constructor
	private DisplayManager(Buffer buffer, JEditTextArea textArea,
		DisplayManager copy)
	{
		this.buffer = buffer;
		this.screenLineMgr = new ScreenLineManager(this,buffer);
		this.textArea = textArea;

		scrollLineCount = new ScrollLineCount(this,textArea);
		firstLine = new FirstLine(this,textArea);

		bufferChangeHandler = new BufferChangeHandler(
			this,textArea,buffer);
		// this listener priority thing is a bad hack...
		buffer.addBufferChangeListener(bufferChangeHandler,
			Buffer.HIGH_PRIORITY);

		if(copy != null)
		{
			folds = new RangeMap(copy.folds);
			initialized = true;
		}
	} //}}}

	//{{{ resetAnchors() method
	private void resetAnchors()
	{
		firstLine.callReset = true;
		scrollLineCount.callReset = true;
		notifyScreenLineChanges();
	} //}}}

	//{{{ dispose() method
	private void dispose()
	{
		buffer.removeBufferChangeListener(bufferChangeHandler);
	} //}}}

	//{{{ showLineRange() method
	private void showLineRange(int start, int end)
	{
		if(Debug.FOLD_VIS_DEBUG)
		{
			Log.log(Log.DEBUG,this,"showLineRange(" + start
				+ "," + end + ")");
		}

		for(int i = start; i <= end; i++)
		{
			//XXX
			if(!isLineVisible(i))
			{
				// important: not screenLineMgr.getScreenLineCount()
				updateScreenLineCount(i);
				int screenLines = getScreenLineCount(i);
				if(firstLine.physicalLine >= i)
				{
					firstLine.scrollLine += screenLines;
					firstLine.callChanged = true;
				}
				scrollLineCount.scrollLine += screenLines;
				scrollLineCount.callChanged = true;
			}
		}

		/* update fold visibility map. */
		folds.show(start,end);
	} //}}}

	//{{{ hideLineRange() method
	private void hideLineRange(int start, int end)
	{
		if(Debug.FOLD_VIS_DEBUG)
		{
			Log.log(Log.DEBUG,this,"hideLineRange(" + start
				+ "," + end + ")");
		}

		int i = start;
		if(!isLineVisible(i))
			i = getNextVisibleLine(i);
		while(i != -1 && i <= end)
		{
			int screenLines = screenLineMgr.getScreenLineCount(i);
			if(i < firstLine.physicalLine)
			{
				firstLine.scrollLine -= screenLines;
				firstLine.skew = 0;
				firstLine.callChanged = true;
			}

			scrollLineCount.scrollLine -= screenLines;
			scrollLineCount.callChanged = true;

			i = getNextVisibleLine(i);
		}

		/* update fold visibility map. */
		folds.hide(start,end);

		if(!isLineVisible(firstLine.physicalLine))
		{
			int firstVisible = getFirstVisibleLine();
			if(firstLine.physicalLine < firstVisible)
			{
				firstLine.physicalLine = firstVisible;
				firstLine.scrollLine = 0;
			}
			else
			{
				firstLine.physicalLine = getPrevVisibleLine(
					firstLine.physicalLine);
				firstLine.scrollLine -=
					screenLineMgr.getScreenLineCount(
					firstLine.physicalLine);
			}
			firstLine.callChanged = true;
		}
	} //}}}

	//{{{ setScreenLineCount() method
	/**
	 * Sets the number of screen lines that the specified physical line
	 * is split into.
	 * @since jEdit 4.2pre1
	 */
	private void setScreenLineCount(int line, int count)
	{
		int oldCount = screenLineMgr.getScreenLineCount(line);

		// old one so that the screen line manager sets the
		// validity flag!

		screenLineMgr.setScreenLineCount(line,count);

		if(count == oldCount)
			return;

		if(!isLineVisible(line))
			return;

		if(firstLine.physicalLine >= line)
		{
			if(firstLine.physicalLine == line)
				firstLine.callChanged = true;
			else
			{
				firstLine.scrollLine += (count - oldCount);
				firstLine.callChanged = true;
			}
		}

		scrollLineCount.scrollLine += (count - oldCount);
		scrollLineCount.callChanged = true;
	} //}}}

	//}}}
}
