/*
 * JEditBuffer.java - jEdit buffer
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1998, 2005 Slava Pestov
 * Portions copyright (C) 1999, 2000 mike dillon
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

package org.gjt.sp.jedit.buffer;

//{{{ Imports
import gnu.regexp.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.Toolkit;
import java.lang.reflect.*;
import java.util.*;
import org.gjt.sp.jedit.Debug;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.TextUtilities;
import org.gjt.sp.jedit.indent.*;
import org.gjt.sp.jedit.syntax.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.util.*;
//}}}

/**
 * A <code>JEditBuffer</code> represents the contents of an open text
 * file as it is maintained in the computer's memory (as opposed to
 * how it may be stored on a disk).<p>
 *
 * This class is partially thread-safe, however you must pay attention to two
 * very important guidelines:
 * <ul>
 * <li>Changes to a buffer can only be made from the AWT thread.
 * <li>When accessing the buffer from another thread, you must
 * grab a read lock if you plan on performing more than one call, to ensure that
 * the buffer contents are not changed by the AWT thread for the duration of the
 * lock. Only methods whose descriptions specify thread safety can be invoked
 * from other threads.
 * </ul>
 *
 * @author Slava Pestov
 * @version $Id$
 *
 * @since jEdit 4.3pre3
 */
public class JEditBuffer
{
	/**
	 * Line separator property.
	 */
	public static final String LINESEP = "lineSeparator";

	/**
	 * Character encoding used when loading and saving.
	 * @since jEdit 3.2pre4
	 */
	public static final String ENCODING = "encoding";

	//{{{ JEditBuffer constructor
	public JEditBuffer(Hashtable props)
	{
		bufferListeners = new Vector();
		lock = new ReadWriteLock();
		contentMgr = new ContentManager();
		lineMgr = new LineManager();
		positionMgr = new PositionManager(this);
		undoMgr = new UndoManager(this);
		seg = new Segment();
		integerArray = new IntegerArray();
		propertyLock = new Object();
		properties = new HashMap();
		indentRules = new ArrayList();
		
		//{{{ need to convert entries of 'props' to PropValue instances
		Enumeration e = props.keys();
		while(e.hasMoreElements())
		{
			Object key = e.nextElement();
			Object value = props.get(key);

			properties.put(key,new PropValue(value,false));
		} //}}}

		// fill in defaults for these from system properties if the
		// corresponding buffer.XXX properties not set
		if(getProperty(ENCODING) == null)
			properties.put(ENCODING,new PropValue(System.getProperty("file.encoding"),false));
		if(getProperty(LINESEP) == null)
			properties.put(LINESEP,new PropValue(System.getProperty("line.separator"),false));
	} //}}}

	//{{{ Flags

	//{{{ isDirty() method
	/**
	 * Returns whether there have been unsaved changes to this buffer.
	 * This method is thread-safe.
	 */
	public boolean isDirty()
	{
		return dirty;
	} //}}}

	//{{{ isLoading() method
	public boolean isLoading()
	{
		return loading;
	} //}}}

	//{{{ setLoading() method
	public void setLoading(boolean loading)
	{
		this.loading = loading;
	} //}}}

	//{{{ isPerformingIO() method
	/**
	 * Returns true if the buffer is currently performing I/O.
	 * This method is thread-safe.
	 * @since jEdit 2.7pre1
	 */
	public boolean isPerformingIO()
	{
		return isLoading() || io;
	} //}}}

	//{{{ setPerformingIO() method
	/**
	 * Returns true if the buffer is currently performing I/O.
	 * This method is thread-safe.
	 * @since jEdit 2.7pre1
	 */
	public void setPerformingIO(boolean io)
	{
		this.io = io;
	} //}}}

	//{{{ isEditable() method
	/**
	 * Returns true if this file is editable, false otherwise. A file may
	 * become uneditable if it is read only, or if I/O is in progress.
	 * This method is thread-safe.
	 * @since jEdit 2.7pre1
	 */
	public boolean isEditable()
	{
		return !(isReadOnly() || isPerformingIO());
	} //}}}

	//{{{ isReadOnly() method
	/**
	 * Returns true if this file is read only, false otherwise.
	 * This method is thread-safe.
	 */
	public boolean isReadOnly()
	{
		return readOnly || readOnlyOverride;
	} //}}}

	//{{{ setReadOnly() method
	/**
	 * Sets the read only flag.
	 * @param readOnly The read only flag
	 */
	public void setReadOnly(boolean readOnly)
	{
		readOnlyOverride = readOnly;
	} //}}}
	
	//{{{ setDirty() method
	/**
	 * Sets the 'dirty' (changed since last save) flag of this buffer.
	 */
	public void setDirty(boolean d)
	{
		boolean editable = isEditable();

		if(d)
		{
			if(editable)
				dirty = true;
		}
		else
		{
			dirty = false;

			// fixes dirty flag not being reset on
			// save/insert/undo/redo/undo
			if(!isUndoInProgress())
			{
				// this ensures that undo can clear the dirty flag properly
				// when all edits up to a save are undone
				undoMgr.bufferSaved();
			}
		}
	} //}}}
	
	//}}}
	
	//{{{ Thread safety

	//{{{ readLock() method
	/**
	 * The buffer is guaranteed not to change between calls to
	 * {@link #readLock()} and {@link #readUnlock()}.
	 */
	public void readLock()
	{
		lock.readLock();
	} //}}}

	//{{{ readUnlock() method
	/**
	 * The buffer is guaranteed not to change between calls to
	 * {@link #readLock()} and {@link #readUnlock()}.
	 */
	public void readUnlock()
	{
		lock.readUnlock();
	} //}}}

	//{{{ writeLock() method
	/**
	 * Attempting to obtain read lock will block between calls to
	 * {@link #writeLock()} and {@link #writeUnlock()}.
	 */
	public void writeLock()
	{
		lock.writeLock();
	} //}}}

	//{{{ writeUnlock() method
	/**
	 * Attempting to obtain read lock will block between calls to
	 * {@link #writeLock()} and {@link #writeUnlock()}.
	 */
	public void writeUnlock()
	{
		lock.writeUnlock();
	} //}}}

	//}}}

	//{{{ Line offset methods

	//{{{ getLength() method
	/**
	 * Returns the number of characters in the buffer. This method is thread-safe.
	 */
	public int getLength()
	{
		// no need to lock since this just returns a value and that's it
		return contentMgr.getLength();
	} //}}}

	//{{{ getLineCount() method
	/**
	 * Returns the number of physical lines in the buffer.
	 * This method is thread-safe.
	 * @since jEdit 3.1pre1
	 */
	public int getLineCount()
	{
		// no need to lock since this just returns a value and that's it
		return lineMgr.getLineCount();
	} //}}}

	//{{{ getLineOfOffset() method
	/**
	 * Returns the line containing the specified offset.
	 * This method is thread-safe.
	 * @param offset The offset
	 * @since jEdit 4.0pre1
	 */
	public int getLineOfOffset(int offset)
	{
		try
		{
			readLock();

			if(offset < 0 || offset > getLength())
				throw new ArrayIndexOutOfBoundsException(offset);

			return lineMgr.getLineOfOffset(offset);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getLineStartOffset() method
	/**
	 * Returns the start offset of the specified line.
	 * This method is thread-safe.
	 * @param line The line
	 * @return The start offset of the specified line
	 * @since jEdit 4.0pre1
	 */
	public int getLineStartOffset(int line)
	{
		try
		{
			readLock();

			if(line < 0 || line >= lineMgr.getLineCount())
				throw new ArrayIndexOutOfBoundsException(line);
			else if(line == 0)
				return 0;

			return lineMgr.getLineEndOffset(line - 1);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getLineEndOffset() method
	/**
	 * Returns the end offset of the specified line.
	 * This method is thread-safe.
	 * @param line The line
	 * @return The end offset of the specified line
	 * invalid.
	 * @since jEdit 4.0pre1
	 */
	public int getLineEndOffset(int line)
	{
		try
		{
			readLock();

			if(line < 0 || line >= lineMgr.getLineCount())
				throw new ArrayIndexOutOfBoundsException(line);

			return lineMgr.getLineEndOffset(line);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getLineLength() method
	/**
	 * Returns the length of the specified line.
	 * This method is thread-safe.
	 * @param line The line
	 * @since jEdit 4.0pre1
	 */
	public int getLineLength(int line)
	{
		try
		{
			readLock();

			return getLineEndOffset(line)
				- getLineStartOffset(line) - 1;
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getPriorNonEmptyLine() method
	/**
	 * Auto indent needs this.
	 */
	public int getPriorNonEmptyLine(int lineIndex)
	{
		int returnValue = -1;

		for(int i = lineIndex - 1; i >= 0; i--)
		{
			getLineText(i,seg);
			if(seg.count != 0)
				returnValue = i;
			for(int j = 0; j < seg.count; j++)
			{
				char ch = seg.array[seg.offset + j];
				if(!Character.isWhitespace(ch))
					return i;
			}
		}

		// didn't find a line that contains non-whitespace chars
		// so return index of prior whitespace line
		return returnValue;
	} //}}}

	//}}}

	//{{{ Text getters and setters

	//{{{ getLineText() method
	/**
	 * Returns the text on the specified line.
	 * This method is thread-safe.
	 * @param line The line
	 * @return The text, or null if the line is invalid
	 * @since jEdit 4.0pre1
	 */
	public String getLineText(int line)
	{
		if(line < 0 || line >= lineMgr.getLineCount())
			throw new ArrayIndexOutOfBoundsException(line);

		try
		{
			readLock();

			int start = (line == 0 ? 0
				: lineMgr.getLineEndOffset(line - 1));
			int end = lineMgr.getLineEndOffset(line);

			return getText(start,end - start - 1);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getLineText() method
	/**
	 * Returns the specified line in a <code>Segment</code>.<p>
	 *
	 * Using a <classname>Segment</classname> is generally more
	 * efficient than using a <classname>String</classname> because it
	 * results in less memory allocation and array copying.<p>
	 *
	 * This method is thread-safe.
	 *
	 * @param line The line
	 * @since jEdit 4.0pre1
	 */
	public void getLineText(int line, Segment segment)
	{
		if(line < 0 || line >= lineMgr.getLineCount())
			throw new ArrayIndexOutOfBoundsException(line);

		try
		{
			readLock();

			int start = (line == 0 ? 0
				: lineMgr.getLineEndOffset(line - 1));
			int end = lineMgr.getLineEndOffset(line);

			getText(start,end - start - 1,segment);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getText() method
	/**
	 * Returns the specified text range. This method is thread-safe.
	 * @param start The start offset
	 * @param length The number of characters to get
	 */
	public String getText(int start, int length)
	{
		try
		{
			readLock();

			if(start < 0 || length < 0
				|| start + length > contentMgr.getLength())
				throw new ArrayIndexOutOfBoundsException(start + ":" + length);

			return contentMgr.getText(start,length);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getText() method
	/**
	 * Returns the specified text range in a <code>Segment</code>.<p>
	 *
	 * Using a <classname>Segment</classname> is generally more
	 * efficient than using a <classname>String</classname> because it
	 * results in less memory allocation and array copying.<p>
	 *
	 * This method is thread-safe.
	 *
	 * @param start The start offset
	 * @param length The number of characters to get
	 * @param seg The segment to copy the text to
	 */
	public void getText(int start, int length, Segment seg)
	{
		try
		{
			readLock();

			if(start < 0 || length < 0
				|| start + length > contentMgr.getLength())
				throw new ArrayIndexOutOfBoundsException(start + ":" + length);

			contentMgr.getText(start,length,seg);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ insert() method
	/**
	 * Inserts a string into the buffer.
	 * @param offset The offset
	 * @param str The string
	 * @since jEdit 4.0pre1
	 */
	public void insert(int offset, String str)
	{
		if(str == null)
			return;

		int len = str.length();

		if(len == 0)
			return;

		if(isReadOnly())
			throw new RuntimeException("buffer read-only");

		try
		{
			writeLock();

			if(offset < 0 || offset > contentMgr.getLength())
				throw new ArrayIndexOutOfBoundsException(offset);

			contentMgr.insert(offset,str);

			integerArray.clear();

			for(int i = 0; i < len; i++)
			{
				if(str.charAt(i) == '\n')
					integerArray.add(i + 1);
			}

			if(!undoInProgress)
			{
				undoMgr.contentInserted(offset,len,str,!dirty);
			}

			contentInserted(offset,len,integerArray);
		}
		finally
		{
			writeUnlock();
		}
	} //}}}

	//{{{ insert() method
	/**
	 * Inserts a string into the buffer.
	 * @param offset The offset
	 * @param seg The segment
	 * @since jEdit 4.0pre1
	 */
	public void insert(int offset, Segment seg)
	{
		if(seg.count == 0)
			return;

		if(isReadOnly())
			throw new RuntimeException("buffer read-only");

		try
		{
			writeLock();

			if(offset < 0 || offset > contentMgr.getLength())
				throw new ArrayIndexOutOfBoundsException(offset);

			contentMgr.insert(offset,seg);

			integerArray.clear();

			for(int i = 0; i < seg.count; i++)
			{
				if(seg.array[seg.offset + i] == '\n')
					integerArray.add(i + 1);
			}

			if(!undoInProgress)
			{
				undoMgr.contentInserted(offset,seg.count,
					seg.toString(),!dirty);
			}

			contentInserted(offset,seg.count,integerArray);
		}
		finally
		{
			writeUnlock();
		}
	} //}}}

	//{{{ remove() method
	/**
	 * Removes the specified rang efrom the buffer.
	 * @param offset The start offset
	 * @param length The number of characters to remove
	 */
	public void remove(int offset, int length)
	{
		if(length == 0)
			return;

		if(isReadOnly())
			throw new RuntimeException("buffer read-only");

		try
		{
			transaction = true;

			writeLock();

			if(offset < 0 || length < 0
				|| offset + length > contentMgr.getLength())
				throw new ArrayIndexOutOfBoundsException(offset + ":" + length);

			int startLine = lineMgr.getLineOfOffset(offset);
			int endLine = lineMgr.getLineOfOffset(offset + length);

			int numLines = endLine - startLine;

			if(!undoInProgress && !loading)
			{
				undoMgr.contentRemoved(offset,length,
					getText(offset,length),
					!dirty);
			}

			firePreContentRemoved(startLine,offset,numLines,length);

			contentMgr.remove(offset,length);
			lineMgr.contentRemoved(startLine,offset,numLines,length);
			positionMgr.contentRemoved(offset,length);

			fireContentRemoved(startLine,offset,numLines,length);

			/* otherwise it will be delivered later */
			if(!undoInProgress && !insideCompoundEdit())
				fireTransactionComplete();

			setDirty(true);
		}
		finally
		{
			transaction = false;

			writeUnlock();
		}
	} //}}}

	//}}}

	//{{{ Indentation

	//{{{ removeTrailingWhiteSpace() method
	/**
	 * Removes trailing whitespace from all lines in the specified list.
	 * @param lines The line numbers
	 * @since jEdit 3.2pre1
	 */
	public void removeTrailingWhiteSpace(int[] lines)
	{
		try
		{
			beginCompoundEdit();

			for(int i = 0; i < lines.length; i++)
			{
				int pos, lineStart, lineEnd, tail;

				getLineText(lines[i],seg);

				// blank line
				if (seg.count == 0) continue;

				lineStart = seg.offset;
				lineEnd = seg.offset + seg.count - 1;

				for (pos = lineEnd; pos >= lineStart; pos--)
				{
					if (!Character.isWhitespace(seg.array[pos]))
						break;
				}

				tail = lineEnd - pos;

				// no whitespace
				if (tail == 0) continue;

				remove(getLineEndOffset(lines[i]) - 1 - tail,tail);
			}
		}
		finally
		{
			endCompoundEdit();
		}
	} //}}}

	//{{{ shiftIndentLeft() method
	/**
	 * Shifts the indent of each line in the specified list to the left.
	 * @param lines The line numbers
	 * @since jEdit 3.2pre1
	 */
	public void shiftIndentLeft(int[] lines)
	{
		int tabSize = getTabSize();
		int indentSize = getIndentSize();
		boolean noTabs = getBooleanProperty("noTabs");

		try
		{
			beginCompoundEdit();

			for(int i = 0; i < lines.length; i++)
			{
				int lineStart = getLineStartOffset(lines[i]);
				String line = getLineText(lines[i]);
				int whiteSpace = MiscUtilities
					.getLeadingWhiteSpace(line);
				if(whiteSpace == 0)
					continue;
				int whiteSpaceWidth = Math.max(0,MiscUtilities
					.getLeadingWhiteSpaceWidth(line,tabSize)
					- indentSize);

				insert(lineStart + whiteSpace,MiscUtilities
					.createWhiteSpace(whiteSpaceWidth,
					(noTabs ? 0 : tabSize)));
				remove(lineStart,whiteSpace);
			}

		}
		finally
		{
			endCompoundEdit();
		}
	} //}}}

	//{{{ shiftIndentRight() method
	/**
	 * Shifts the indent of each line in the specified list to the right.
	 * @param lines The line numbers
	 * @since jEdit 3.2pre1
	 */
	public void shiftIndentRight(int[] lines)
	{
		try
		{
			beginCompoundEdit();

			int tabSize = getTabSize();
			int indentSize = getIndentSize();
			boolean noTabs = getBooleanProperty("noTabs");
			for(int i = 0; i < lines.length; i++)
			{
				int lineStart = getLineStartOffset(lines[i]);
				String line = getLineText(lines[i]);
				int whiteSpace = MiscUtilities
					.getLeadingWhiteSpace(line);

				// silly usability hack
				//if(lines.length != 1 && whiteSpace == 0)
				//	continue;

				int whiteSpaceWidth = MiscUtilities
					.getLeadingWhiteSpaceWidth(
					line,tabSize) + indentSize;
				insert(lineStart + whiteSpace,MiscUtilities
					.createWhiteSpace(whiteSpaceWidth,
					(noTabs ? 0 : tabSize)));
				remove(lineStart,whiteSpace);
			}
		}
		finally
		{
			endCompoundEdit();
		}
	} //}}}

	//{{{ indentLines() method
	/**
	 * Indents all specified lines.
	 * @param start The first line to indent
	 * @param end The last line to indent
	 * @since jEdit 3.1pre3
	 */
	public void indentLines(int start, int end)
	{
		try
		{
			beginCompoundEdit();
			for(int i = start; i <= end; i++)
				indentLine(i,true);
		}
		finally
		{
			endCompoundEdit();
		}
	} //}}}

	//{{{ indentLines() method
	/**
	 * Indents all specified lines.
	 * @param lines The line numbers
	 * @since jEdit 3.2pre1
	 */
	public void indentLines(int[] lines)
	{
		try
		{
			beginCompoundEdit();
			for(int i = 0; i < lines.length; i++)
				indentLine(lines[i],true);
		}
		finally
		{
			endCompoundEdit();
		}
	} //}}}

	//{{{ indentLine() method
	/**
	 * @deprecated Use {@link #indentLine(int,boolean)} instead.
	 */
	public boolean indentLine(int lineIndex, boolean canIncreaseIndent,
		boolean canDecreaseIndent)
	{
		return indentLine(lineIndex,canDecreaseIndent);
	} //}}}

	//{{{ indentLine() method
	/**
	 * Indents the specified line.
	 * @param lineIndex The line number to indent
	 * @param canDecreaseIndent If true, the indent can be decreased as a
	 * result of this. Set this to false for Tab key.
	 * @return true If indentation took place, false otherwise.
	 * @since jEdit 4.2pre2
	 */
	public boolean indentLine(int lineIndex, boolean canDecreaseIndent)
	{
		int[] whitespaceChars = new int[1];
		int currentIndent = getCurrentIndentForLine(lineIndex,
			whitespaceChars);
		int idealIndent = getIdealIndentForLine(lineIndex);

		if(idealIndent == -1 || idealIndent == currentIndent
			|| (!canDecreaseIndent && idealIndent < currentIndent))
			return false;

		// Do it
		try
		{
			beginCompoundEdit();

			int start = getLineStartOffset(lineIndex);

			remove(start,whitespaceChars[0]);
			insert(start,MiscUtilities.createWhiteSpace(
				idealIndent,(getBooleanProperty("noTabs")
				? 0 : getTabSize())));
		}
		finally
		{
			endCompoundEdit();
		}

		return true;
	} //}}}

	//{{{ getCurrentIndentForLine() method
	/**
	 * Returns the line's current leading indent.
	 * @param lineIndex The line number
	 * @param whitespaceChars If this is non-null, the number of whitespace
	 * characters is stored at the 0 index
	 * @since jEdit 4.2pre2
	 */
	public int getCurrentIndentForLine(int lineIndex, int[] whitespaceChars)
	{
		getLineText(lineIndex,seg);

		int tabSize = getTabSize();

		int currentIndent = 0;
loop:		for(int i = 0; i < seg.count; i++)
		{
			char c = seg.array[seg.offset + i];
			switch(c)
			{
			case ' ':
				currentIndent++;
				if(whitespaceChars != null)
					whitespaceChars[0]++;
				break;
			case '\t':
				currentIndent += (tabSize - (currentIndent
					% tabSize));
				if(whitespaceChars != null)
					whitespaceChars[0]++;
				break;
			default:
				break loop;
			}
		}

		return currentIndent;
	} //}}}

	//{{{ getIdealIndentForLine() method
	/**
	 * Returns the ideal leading indent for the specified line.
	 * This will apply the various auto-indent rules.
	 * @param lineIndex The line number
	 */
	public int getIdealIndentForLine(int lineIndex)
	{
		int prevLineIndex = getPriorNonEmptyLine(lineIndex);
		int prevPrevLineIndex = prevLineIndex < 0 ? -1
			: getPriorNonEmptyLine(prevLineIndex);

		int oldIndent = (prevLineIndex == -1 ? 0 :
			MiscUtilities.getLeadingWhiteSpaceWidth(
			getLineText(prevLineIndex),
			getTabSize()));
		int newIndent = oldIndent;

		Iterator rules = indentRules.iterator();

		List actions = new LinkedList();

		while(rules.hasNext())
		{
			IndentRule rule = (IndentRule)rules.next();
			rule.apply(this,lineIndex,prevLineIndex,
				prevPrevLineIndex,actions);
		}

		Iterator actionIter = actions.iterator();

		while(actionIter.hasNext())
		{
			IndentAction action = (IndentAction)actionIter.next();
			newIndent = action.calculateIndent(this,lineIndex,
				oldIndent,newIndent);
			if(newIndent < 0)
				newIndent = 0;

			if(!action.keepChecking())
				break;
		}

		return newIndent;
	} //}}}

	//{{{ getVirtualWidth() method
	/**
	 * Returns the virtual column number (taking tabs into account) of the
	 * specified position.
	 *
	 * @param line The line number
	 * @param column The column number
	 * @since jEdit 4.1pre1
	 */
	public int getVirtualWidth(int line, int column)
	{
		try
		{
			readLock();

			int start = getLineStartOffset(line);
			getText(start,column,seg);

			return MiscUtilities.getVirtualWidth(seg,getTabSize());
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ getOffsetOfVirtualColumn() method
	/**
	 * Returns the offset of a virtual column number (taking tabs
	 * into account) relative to the start of the line in question.
	 *
	 * @param line The line number
	 * @param column The virtual column number
	 * @param totalVirtualWidth If this array is non-null, the total
	 * virtual width will be stored in its first location if this method
	 * returns -1.
	 *
	 * @return -1 if the column is out of bounds
	 *
	 * @since jEdit 4.1pre1
	 */
	public int getOffsetOfVirtualColumn(int line, int column,
		int[] totalVirtualWidth)
	{
		try
		{
			readLock();

			getLineText(line,seg);

			return MiscUtilities.getOffsetOfVirtualColumn(seg,
				getTabSize(),column,totalVirtualWidth);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//{{{ insertAtColumn() method
	/**
	 * Like the {@link #insert(int,String)} method, but inserts the string at
	 * the specified virtual column. Inserts spaces as appropriate if
	 * the line is shorter than the column.
	 * @param line The line number
	 * @param col The virtual column number
	 * @param str The string
	 */
	public void insertAtColumn(int line, int col, String str)
	{
		try
		{
			writeLock();

			int[] total = new int[1];
			int offset = getOffsetOfVirtualColumn(line,col,total);
			if(offset == -1)
			{
				offset = getLineEndOffset(line) - 1;
				str = MiscUtilities.createWhiteSpace(col - total[0],0) + str;
			}
			else
				offset += getLineStartOffset(line);

			insert(offset,str);
		}
		finally
		{
			writeUnlock();
		}
	} //}}}

	//{{{ insertIndented() method
	/**
	 * Inserts a string into the buffer, indenting each line of the string
	 * to match the indent of the first line.
	 *
	 * @param offset The offset
	 * @param text The text
	 *
	 * @return The number of characters of indent inserted on each new
	 * line. This is used by the abbreviations code.
	 *
	 * @since jEdit 4.2pre14
	 */
	public int insertIndented(int offset, String text)
	{
		try
		{
			beginCompoundEdit();

			// obtain the leading indent for later use
			int firstLine = getLineOfOffset(offset);
			String lineText = getLineText(firstLine);
			int leadingIndent
				= MiscUtilities.getLeadingWhiteSpaceWidth(
				lineText,getTabSize());

			String whiteSpace = MiscUtilities.createWhiteSpace(
				leadingIndent,getBooleanProperty("noTabs")
				? 0 : getTabSize());

			insert(offset,text);

			int lastLine = getLineOfOffset(offset + text.length());

			// note that if firstLine == lastLine, loop does not
			// execute
			for(int i = firstLine + 1; i <= lastLine; i++)
			{
				insert(getLineStartOffset(i),whiteSpace);
			}

			return whiteSpace.length();
		}
		finally
		{
			endCompoundEdit();
		}
	} //}}}

	//{{{ isElectricKey() method
	/**
	 * Should inserting this character trigger a re-indent of
	 * the current line?
	 * @since jEdit 4.3pre2
	 */
	public boolean isElectricKey(char ch)
	{
		return electricKeys.indexOf(ch) != -1;
	} //}}}
	
	//}}}

	//{{{ Syntax highlighting
	
	//{{{ markTokens() method
	/**
	 * Returns the syntax tokens for the specified line.
	 * @param lineIndex The line number
	 * @param tokenHandler The token handler that will receive the syntax
	 * tokens
	 * @since jEdit 4.1pre1
	 */
	public void markTokens(int lineIndex, TokenHandler tokenHandler)
	{
		Segment seg;
		if(SwingUtilities.isEventDispatchThread())
			seg = this.seg;
		else
			seg = new Segment();

		if(lineIndex < 0 || lineIndex >= lineMgr.getLineCount())
			throw new ArrayIndexOutOfBoundsException(lineIndex);

		int firstInvalidLineContext = lineMgr.getFirstInvalidLineContext();
		int start;
		if(textMode || firstInvalidLineContext == -1)
		{
			start = lineIndex;
		}
		else
		{
			start = Math.min(firstInvalidLineContext,
				lineIndex);
		}

		if(Debug.TOKEN_MARKER_DEBUG)
			Log.log(Log.DEBUG,this,"tokenize from " + start + " to " + lineIndex);
		TokenMarker.LineContext oldContext = null;
		TokenMarker.LineContext context = null;
		for(int i = start; i <= lineIndex; i++)
		{
			getLineText(i,seg);

			oldContext = lineMgr.getLineContext(i);

			TokenMarker.LineContext prevContext = (
				(i == 0 || textMode) ? null
				: lineMgr.getLineContext(i - 1)
			);

			context = tokenMarker.markTokens(prevContext,
				(i == lineIndex ? tokenHandler
				: DummyTokenHandler.INSTANCE),seg);
			lineMgr.setLineContext(i,context);
		}

		int lineCount = lineMgr.getLineCount();
		if(lineCount - 1 == lineIndex)
			lineMgr.setFirstInvalidLineContext(-1);
		else if(oldContext != context)
			lineMgr.setFirstInvalidLineContext(lineIndex + 1);
		else if(firstInvalidLineContext == -1)
			/* do nothing */;
		else
		{
			lineMgr.setFirstInvalidLineContext(Math.max(
				firstInvalidLineContext,lineIndex + 1));
		}
	} //}}}

	//{{{ getTokenMarker() method
	public TokenMarker getTokenMarker()
	{
		return tokenMarker;
	} //}}}

	//{{{ setTokenMarker() method
	public void setTokenMarker(TokenMarker tokenMarker)
	{
		TokenMarker oldTokenMarker = this.tokenMarker;

		this.tokenMarker = tokenMarker;

		// don't do this on initial token marker
		if(oldTokenMarker != null && tokenMarker != oldTokenMarker)
		{
			lineMgr.setFirstInvalidLineContext(0);
		}
	} //}}}

	//{{{ createPosition() method
	/**
	 * Creates a floating position.
	 * @param offset The offset
	 */
	public Position createPosition(int offset)
	{
		try
		{
			readLock();

			if(offset < 0 || offset > contentMgr.getLength())
				throw new ArrayIndexOutOfBoundsException(offset);

			return positionMgr.createPosition(offset);
		}
		finally
		{
			readUnlock();
		}
	} //}}}

	//}}}
	
	//{{{ Property methods

	//{{{ getTabSize() method
	/**
	 * Returns the tab size used in this buffer. This is equivalent
	 * to calling <code>getProperty("tabSize")</code>.
	 * This method is thread-safe.
	 */
	public int getTabSize()
	{
		int tabSize = getIntegerProperty("tabSize",8);
		if(tabSize <= 0)
			return 8;
		else
			return tabSize;
	} //}}}

	//{{{ getIndentSize() method
	/**
	 * Returns the indent size used in this buffer. This is equivalent
	 * to calling <code>getProperty("indentSize")</code>.
	 * This method is thread-safe.
	 * @since jEdit 2.7pre1
	 */
	public int getIndentSize()
	{
		int indentSize = getIntegerProperty("indentSize",8);
		if(indentSize <= 0)
			return 8;
		else
			return indentSize;
	} //}}}

	//{{{ getProperty() method
	/**
	 * Returns the value of a buffer-local property.<p>
	 *
	 * Using this method is generally discouraged, because it returns an
	 * <code>Object</code> which must be cast to another type
	 * in order to be useful, and this can cause problems if the object
	 * is of a different type than what the caller expects.<p>
	 *
	 * The following methods should be used instead:
	 * <ul>
	 * <li>{@link #getStringProperty(String)}</li>
	 * <li>{@link #getBooleanProperty(String)}</li>
	 * <li>{@link #getIntegerProperty(String,int)}</li>
	 * <li>{@link #getRegexpProperty(String,int,gnu.regexp.RESyntax)}</li>
	 * </ul>
	 *
	 * This method is thread-safe.
	 *
	 * @param name The property name. For backwards compatibility, this
	 * is an <code>Object</code>, not a <code>String</code>.
	 */
	public Object getProperty(Object name)
	{
		synchronized(propertyLock)
		{
			// First try the buffer-local properties
			PropValue o = (PropValue)properties.get(name);
			if(o != null)
				return o.value;

			// For backwards compatibility
			if(!(name instanceof String))
				return null;

			Object retVal = getDefaultProperty((String)name);

			if(retVal == null)
				return null;
			else
			{
				properties.put(name,new PropValue(retVal,true));
				return retVal;
			}
		}
	} //}}}
	
	//{{{ getDefaultProperty() method
	public Object getDefaultProperty(String key)
	{
		return null;
	} //}}}
	
	//{{{ setProperty() method
	/**
	 * Sets the value of a buffer-local property.
	 * @param name The property name
	 * @param value The property value
	 * @since jEdit 4.0pre1
	 */
	public void setProperty(String name, Object value)
	{
		if(value == null)
			properties.remove(name);
		else
		{
			PropValue test = (PropValue)properties.get(name);
			if(test == null)
				properties.put(name,new PropValue(value,false));
			else if(test.value.equals(value))
			{
				// do nothing
			}
			else
			{
				test.value = value;
				test.defaultValue = false;
			}
		}
	} //}}}

	//{{{ setDefaultProperty() method
	public void setDefaultProperty(String name, Object value)
	{
		properties.put(name,new PropValue(value,true));
	} //}}}

	//{{{ unsetProperty() method
	/**
	 * Clears the value of a buffer-local property.
	 * @param name The property name
	 * @since jEdit 4.0pre1
	 */
	public void unsetProperty(String name)
	{
		properties.remove(name);
	} //}}}

	//{{{ resetCachedProperties() method
	public void resetCachedProperties()
	{
		// Need to reset properties that were cached defaults,
		// since the defaults might have changed.
		Iterator iter = properties.values().iterator();
		while(iter.hasNext())
		{
			PropValue value = (PropValue)iter.next();
			if(value.defaultValue)
				iter.remove();
		}
	} //}}}

	//{{{ getStringProperty() method
	/**
	 * Returns the value of a string property. This method is thread-safe.
	 * @param name The property name
	 * @since jEdit 4.0pre1
	 */
	public String getStringProperty(String name)
	{
		Object obj = getProperty(name);
		if(obj != null)
			return obj.toString();
		else
			return null;
	} //}}}

	//{{{ setStringProperty() method
	/**
	 * Sets a string property.
	 * @param name The property name
	 * @param value The value
	 * @since jEdit 4.0pre1
	 */
	public void setStringProperty(String name, String value)
	{
		setProperty(name,value);
	} //}}}

	//{{{ getBooleanProperty() method
	/**
	 * Returns the value of a boolean property. This method is thread-safe.
	 * @param name The property name
	 * @since jEdit 4.0pre1
	 */
	public boolean getBooleanProperty(String name)
	{
		Object obj = getProperty(name);
		if(obj instanceof Boolean)
			return ((Boolean)obj).booleanValue();
		else if("true".equals(obj) || "on".equals(obj) || "yes".equals(obj))
			return true;
		else
			return false;
	} //}}}

	//{{{ setBooleanProperty() method
	/**
	 * Sets a boolean property.
	 * @param name The property name
	 * @param value The value
	 * @since jEdit 4.0pre1
	 */
	public void setBooleanProperty(String name, boolean value)
	{
		setProperty(name,value ? Boolean.TRUE : Boolean.FALSE);
	} //}}}

	//{{{ getIntegerProperty() method
	/**
	 * Returns the value of an integer property. This method is thread-safe.
	 * @param name The property name
	 * @since jEdit 4.0pre1
	 */
	public int getIntegerProperty(String name, int defaultValue)
	{
		boolean defaultValueFlag;
		Object obj;
		PropValue value = (PropValue)properties.get(name);
		if(value != null)
		{
			obj = value.value;
			defaultValueFlag = value.defaultValue;
		}
		else
		{
			obj = getProperty(name);
			// will be cached from now on...
			defaultValueFlag = true;
		}

		if(obj == null)
			return defaultValue;
		else if(obj instanceof Number)
			return ((Number)obj).intValue();
		else
		{
			try
			{
				int returnValue = Integer.parseInt(
					obj.toString().trim());
				properties.put(name,new PropValue(
					new Integer(returnValue),
					defaultValueFlag));
				return returnValue;
			}
			catch(Exception e)
			{
				return defaultValue;
			}
		}
	} //}}}

	//{{{ setIntegerProperty() method
	/**
	 * Sets an integer property.
	 * @param name The property name
	 * @param value The value
	 * @since jEdit 4.0pre1
	 */
	public void setIntegerProperty(String name, int value)
	{
		setProperty(name,new Integer(value));
	} //}}}

	//{{{ getRegexpProperty() method
	/**
	 * Returns the value of a property as a regular expression.
	 * This method is thread-safe.
	 * @param name The property name
	 * @param cflags Regular expression compilation flags
	 * @param syntax Regular expression syntax
	 * @since jEdit 4.1pre9
	 */
	public RE getRegexpProperty(String name, int cflags,
		RESyntax syntax) throws REException
	{
		synchronized(propertyLock)
		{
			boolean defaultValueFlag;
			Object obj;
			PropValue value = (PropValue)properties.get(name);
			if(value != null)
			{
				obj = value.value;
				defaultValueFlag = value.defaultValue;
			}
			else
			{
				obj = getProperty(name);
				// will be cached from now on...
				defaultValueFlag = true;
			}

			if(obj == null)
				return null;
			else if(obj instanceof RE)
				return (RE)obj;
			else
			{
				RE re = new RE(obj.toString(),cflags,syntax);
				properties.put(name,new PropValue(re,
					defaultValueFlag));
				return re;
			}
		}
	} //}}}

	//{{{ getRuleSetAtOffset() method
	/**
	 * Returns the syntax highlighting ruleset at the specified offset.
	 * @since jEdit 4.1pre1
	 */
	public ParserRuleSet getRuleSetAtOffset(int offset)
	{
		int line = getLineOfOffset(offset);
		offset -= getLineStartOffset(line);
		if(offset != 0)
			offset--;

		DefaultTokenHandler tokens = new DefaultTokenHandler();
		markTokens(line,tokens);
		Token token = TextUtilities.getTokenAtOffset(tokens.getTokens(),offset);
		return token.rules;
	} //}}}

	//{{{ getKeywordMapAtOffset() method
	/**
	 * Returns the syntax highlighting keyword map in effect at the
	 * specified offset. Used by the <b>Complete Word</b> command to
	 * complete keywords.
	 * @param offset The offset
	 * @since jEdit 4.0pre3
	 */
	public KeywordMap getKeywordMapAtOffset(int offset)
	{
		return getRuleSetAtOffset(offset).getKeywords();
	} //}}}

	//{{{ getContextSensitiveProperty() method
	/**
	 * Some settings, like comment start and end strings, can
	 * vary between different parts of a buffer (HTML text and inline
	 * JavaScript, for example).
	 * @param offset The offset
	 * @param name The property name
	 * @since jEdit 4.0pre3
	 */
	public String getContextSensitiveProperty(int offset, String name)
	{
		ParserRuleSet rules = getRuleSetAtOffset(offset);

		Object value = null;

		Hashtable rulesetProps = rules.getProperties();
		if(rulesetProps != null)
			value = rulesetProps.get(name);

		if(value == null)
			return null;
		else
			return String.valueOf(value);
	} //}}}

	//}}}
	
	//{{{ Folding methods

	//{{{ isFoldStart() method
	/**
	 * Returns if the specified line begins a fold.
	 * @since jEdit 3.1pre1
	 */
	public boolean isFoldStart(int line)
	{
		return (line != getLineCount() - 1
			&& getFoldLevel(line) < getFoldLevel(line + 1));
	} //}}}

	//{{{ isFoldEnd() method
	/**
	 * Returns if the specified line ends a fold.
	 * @since jEdit 4.2pre5
	 */
	public boolean isFoldEnd(int line)
	{
		return (line != getLineCount() - 1
			&& getFoldLevel(line) > getFoldLevel(line + 1));
	} //}}}

	//{{{ invalidateCachedFoldLevels() method
	/**
	 * Invalidates all cached fold level information.
	 * @since jEdit 4.1pre11
	 */
	public void invalidateCachedFoldLevels()
	{
		lineMgr.setFirstInvalidFoldLevel(0);
		fireFoldLevelChanged(0,getLineCount());
	} //}}}

	//{{{ getFoldLevel() method
	/**
	 * Returns the fold level of the specified line.
	 * @param line A physical line index
	 * @since jEdit 3.1pre1
	 */
	public int getFoldLevel(int line)
	{
		if(line < 0 || line >= lineMgr.getLineCount())
			throw new ArrayIndexOutOfBoundsException(line);

		if(foldHandler instanceof DummyFoldHandler)
			return 0;

		int firstInvalidFoldLevel = lineMgr.getFirstInvalidFoldLevel();
		if(firstInvalidFoldLevel == -1 || line < firstInvalidFoldLevel)
		{
			return lineMgr.getFoldLevel(line);
		}
		else
		{
			if(Debug.FOLD_DEBUG)
				Log.log(Log.DEBUG,this,"Invalid fold levels from " + firstInvalidFoldLevel + " to " + line);

			int newFoldLevel = 0;
			boolean changed = false;

			for(int i = firstInvalidFoldLevel; i <= line; i++)
			{
				newFoldLevel = foldHandler.getFoldLevel(this,i,seg);
				if(newFoldLevel != lineMgr.getFoldLevel(i))
				{
					if(Debug.FOLD_DEBUG)
						Log.log(Log.DEBUG,this,i + " fold level changed");
					changed = true;
				}
				lineMgr.setFoldLevel(i,newFoldLevel);
			}

			if(line == lineMgr.getLineCount() - 1)
				lineMgr.setFirstInvalidFoldLevel(-1);
			else
				lineMgr.setFirstInvalidFoldLevel(line + 1);

			if(changed)
			{
				if(Debug.FOLD_DEBUG)
					Log.log(Log.DEBUG,this,"fold level changed: " + firstInvalidFoldLevel + "," + line);
				fireFoldLevelChanged(firstInvalidFoldLevel,line);
			}

			return newFoldLevel;
		}
	} //}}}

	//{{{ getFoldAtLine() method
	/**
	 * Returns an array. The first element is the start line, the
	 * second element is the end line, of the fold containing the
	 * specified line number.
	 * @param line The line number
	 * @since jEdit 4.0pre3
	 */
	public int[] getFoldAtLine(int line)
	{
		int start, end;

		if(isFoldStart(line))
		{
			start = line;
			int foldLevel = getFoldLevel(line);

			line++;

			while(getFoldLevel(line) > foldLevel)
			{
				line++;

				if(line == getLineCount())
					break;
			}

			end = line - 1;
		}
		else
		{
			start = line;
			int foldLevel = getFoldLevel(line);
			while(getFoldLevel(start) >= foldLevel)
			{
				if(start == 0)
					break;
				else
					start--;
			}

			end = line;
			while(getFoldLevel(end) >= foldLevel)
			{
				end++;

				if(end == getLineCount())
					break;
			}

			end--;
		}

		while(getLineLength(end) == 0 && end > start)
			end--;

		return new int[] { start, end };
	} //}}}

	//{{{ getFoldHandler() method
	/**
	 * Returns the current buffer's fold handler.
	 * @since jEdit 4.2pre1
	 */
	public FoldHandler getFoldHandler()
	{
		return foldHandler;
	} //}}}

	//{{{ setFoldHandler() method
	/**
	 * Sets the buffer's fold handler.
	 * @since jEdit 4.2pre2
	 */
	public void setFoldHandler(FoldHandler foldHandler)
	{
		FoldHandler oldFoldHandler = this.foldHandler;

		if(foldHandler.equals(oldFoldHandler))
			return;

		this.foldHandler = foldHandler;

		lineMgr.setFirstInvalidFoldLevel(0);

		fireFoldHandlerChanged();
	} //}}}

	//}}}

	//{{{ Undo

	//{{{ undo() method
	/**
	 * Undoes the most recent edit.
	 *
	 * @since jEdit 4.0pre1
	 */
	public void undo(JEditTextArea textArea)
	{
		if(undoMgr == null)
			return;

		if(!isEditable())
		{
			textArea.getToolkit().beep();
			return;
		}

		try
		{
			writeLock();

			undoInProgress = true;
			int caret = undoMgr.undo();

			if(caret == -1)
				textArea.getToolkit().beep();
			else
				textArea.setCaretPosition(caret);

			fireTransactionComplete();
		}
		finally
		{
			undoInProgress = false;

			writeUnlock();
		}
	} //}}}

	//{{{ redo() method
	/**
	 * Redoes the most recently undone edit.
	 *
	 * @since jEdit 2.7pre2
	 */
	public void redo(JEditTextArea textArea)
	{
		if(undoMgr == null)
			return;

		if(!isEditable())
		{
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		try
		{
			writeLock();

			undoInProgress = true;
			int caret = undoMgr.redo();
			if(caret == -1)
				textArea.getToolkit().beep();
			else
				textArea.setCaretPosition(caret);

			fireTransactionComplete();
		}
		finally
		{
			undoInProgress = false;

			writeUnlock();
		}
	} //}}}

	//{{{ isTransactionInProgress() method
	/**
	 * Returns if an undo or compound edit is currently in progress. If this
	 * method returns true, then eventually a
	 * {@link org.gjt.sp.jedit.buffer.BufferChangeListener#transactionComplete(Buffer)}
	 * buffer event will get fired.
	 * @since jEdit 4.0pre6
	 */
	public boolean isTransactionInProgress()
	{
		return transaction || undoInProgress || insideCompoundEdit();
	} //}}}

	//{{{ beginCompoundEdit() method
	/**
	 * Starts a compound edit. All edits from now on until
	 * {@link #endCompoundEdit()} are called will be merged
	 * into one. This can be used to make a complex operation
	 * undoable in one step. Nested calls to
	 * {@link #beginCompoundEdit()} behave as expected,
	 * requiring the same number of {@link #endCompoundEdit()}
	 * calls to end the edit.
	 * @see #endCompoundEdit()
	 */
	public void beginCompoundEdit()
	{
		try
		{
			writeLock();

			undoMgr.beginCompoundEdit();
		}
		finally
		{
			writeUnlock();
		}
	} //}}}

	//{{{ endCompoundEdit() method
	/**
	 * Ends a compound edit. All edits performed since
	 * {@link #beginCompoundEdit()} was called can now
	 * be undone in one step by calling {@link #undo(JEditTextArea)}.
	 * @see #beginCompoundEdit()
	 */
	public void endCompoundEdit()
	{
		try
		{
			writeLock();

			undoMgr.endCompoundEdit();

			if(!insideCompoundEdit())
				fireTransactionComplete();
		}
		finally
		{
			writeUnlock();
		}
	}//}}}

	//{{{ insideCompoundEdit() method
	/**
	 * Returns if a compound edit is currently active.
	 * @since jEdit 3.1pre1
	 */
	public boolean insideCompoundEdit()
	{
		return undoMgr.insideCompoundEdit();
	} //}}}

	//{{{ isUndoInProgress() method
	/**
	 * Returns if an undo or redo is currently being performed.
	 * @since jEdit 4.3pre3
	 */
	public boolean isUndoInProgress()
	{
		return undoInProgress;
	} //}}}

	//}}}

	//{{{ Buffer events
	public static final int NORMAL_PRIORITY = 0;
	public static final int HIGH_PRIORITY = 1;

	static class Listener
	{
		BufferListener listener;
		int priority;

		Listener(BufferListener listener, int priority)
		{
			this.listener = listener;
			this.priority = priority;
		}
	}

	//{{{ addBufferListener() method
	/**
	 * Adds a buffer change listener.
	 * @param listener The listener
	 * @param priority Listeners with HIGH_PRIORITY get the event before
	 * listeners with NORMAL_PRIORITY
	 * @since jEdit 4.3pre3
	 */
	public void addBufferListener(BufferListener listener,
		int priority)
	{
		Listener l = new Listener(listener,priority);
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			Listener _l = (Listener)bufferListeners.get(i);
			if(_l.priority < priority)
			{
				bufferListeners.insertElementAt(l,i);
				return;
			}
		}
		bufferListeners.addElement(l);
	} //}}}

	//{{{ addBufferListener() method
	/**
	 * Adds a buffer change listener.
	 * @param listener The listener
	 * @since jEdit 4.3pre3
	 */
	public void addBufferListener(BufferListener listener)
	{
		addBufferListener(listener,NORMAL_PRIORITY);
	} //}}}

	//{{{ removeBufferListener() method
	/**
	 * Removes a buffer change listener.
	 * @param listener The listener
	 * @since jEdit 4.3pre3
	 */
	public void removeBufferListener(BufferListener listener)
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			if(((Listener)bufferListeners.get(i)).listener == listener)
			{
				bufferListeners.removeElementAt(i);
				return;
			}
		}
	} //}}}

	//{{{ getBufferListeners() method
	/**
	 * Returns an array of registered buffer change listeners.
	 * @since jEdit 4.3pre3
	 */
	public BufferListener[] getBufferListeners()
	{
		BufferListener[] returnValue
			= new BufferListener[
			bufferListeners.size()];
		for(int i = 0; i < returnValue.length; i++)
		{
			returnValue[i] = ((Listener)bufferListeners.get(i))
				.listener;
		}
		return returnValue;
	} //}}}

	//}}}
	
	//{{{ Protected members
	
	protected Segment seg;
	protected boolean textMode;
	protected UndoManager undoMgr;

	//{{{ Event firing methods

	//{{{ fireFoldLevelChanged() method
	protected void fireFoldLevelChanged(int start, int end)
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			try
			{
				getListener(i).foldLevelChanged(this,start,end);
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,this,"Exception while sending buffer event to "+getListener(i)+" :");
				Log.log(Log.ERROR,this,t);
			}
		}
	} //}}}

	//{{{ fireContentInserted() method
	protected void fireContentInserted(int startLine, int offset,
		int numLines, int length)
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			try
			{
				getListener(i).contentInserted(this,startLine,
					offset,numLines,length);
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,this,"Exception while sending buffer event to "+getListener(i)+" :");
				Log.log(Log.ERROR,this,t);
			}
		}
	} //}}}

	//{{{ fireContentRemoved() method
	protected void fireContentRemoved(int startLine, int offset,
		int numLines, int length)
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			try
			{
				getListener(i).contentRemoved(this,startLine,
					offset,numLines,length);
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,this,"Exception while sending buffer event to "+getListener(i)+" :");
				Log.log(Log.ERROR,this,t);
			}
		}
	} //}}}

	//{{{ firePreContentRemoved() method
	protected void firePreContentRemoved(int startLine, int offset,
		int numLines, int length)
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			try
			{
				getListener(i).preContentRemoved(this,startLine,
					offset,numLines,length);
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,this,"Exception while sending buffer event to "+getListener(i)+" :");
				Log.log(Log.ERROR,this,t);
			}
		}
	} //}}}

	//{{{ fireTransactionComplete() method
	protected void fireTransactionComplete()
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			try
			{
				getListener(i).transactionComplete(this);
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,this,"Exception while sending buffer event to "+getListener(i)+" :");
				Log.log(Log.ERROR,this,t);
			}
		}
	} //}}}

	//{{{ fireFoldHandlerChanged() method
	protected void fireFoldHandlerChanged()
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			try
			{
				getListener(i).foldHandlerChanged(this);
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,this,"Exception while sending buffer event to "+getListener(i)+" :");
				Log.log(Log.ERROR,this,t);
			}
		}
	} //}}}

	//{{{ fireBufferLoaded() method
	protected void fireBufferLoaded()
	{
		for(int i = 0; i < bufferListeners.size(); i++)
		{
			try
			{
				getListener(i).bufferLoaded(this);
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,this,"Exception while sending buffer event to "+getListener(i)+" :");
				Log.log(Log.ERROR,this,t);
			}
		}
	} //}}}

	//}}}
	
	//{{{ isFileReadOnly() method
	protected boolean isFileReadOnly()
	{
		return readOnly;
	} //}}}

	//{{{ setFileReadOnly() method
	protected void setFileReadOnly(boolean readOnly)
	{
		this.readOnly = readOnly;
	} //}}}

	//{{{ loadText() method
	protected void loadText(Segment seg, IntegerArray endOffsets)
	{
		if(seg == null)
			seg = new Segment(new char[1024],0,0);

		if(endOffsets == null)
		{
			endOffsets = new IntegerArray();
			endOffsets.add(1);
		}

		try
		{
			writeLock();

			// For `reload' command
			// contentMgr.remove() changes this!
			int length = getLength();

			firePreContentRemoved(0,0,getLineCount()
				- 1,length);

			contentMgr.remove(0,length);
			lineMgr.contentRemoved(0,0,getLineCount()
				- 1,length);
			positionMgr.contentRemoved(0,length);
			fireContentRemoved(0,0,getLineCount()
				- 1,length);

			// theoretically a segment could
			// have seg.offset != 0 but
			// SegmentBuffer never does that
			contentMgr._setContent(seg.array,seg.count);

			lineMgr._contentInserted(endOffsets);
			positionMgr.contentInserted(0,seg.count);

			fireContentInserted(0,0,
				endOffsets.getSize() - 1,
				seg.count - 1);
		}
		finally
		{
			writeUnlock();
		}
	} //}}}

	//{{{ invalidateFoldLevels() method
	protected void invalidateFoldLevels()
	{
		lineMgr.setFirstInvalidFoldLevel(0);
	} //}}}
	
	//{{{ parseBufferLocalProperties() method
	protected void parseBufferLocalProperties()
	{
		int lastLine = Math.min(9,getLineCount() - 1);
		parseBufferLocalProperties(getText(0,getLineEndOffset(lastLine) - 1));

		// first line for last 10 lines, make sure not to overlap
		// with the first 10
		int firstLine = Math.max(lastLine + 1, getLineCount() - 10);
		if(firstLine < getLineCount())
		{
			int length = getLineEndOffset(getLineCount() - 1)
				- (getLineStartOffset(firstLine) + 1);
			parseBufferLocalProperties(getText(getLineStartOffset(firstLine),length));
		}
	} //}}}
	
	//{{{ initIndentRules() method
	protected void initIndentRules()
	{
		indentRules.clear();

		if (getBooleanProperty("deepIndent"))
			indentRules.add(new DeepIndentRule());

		String[] regexpProps = {
			"indentNextLine",
			"indentNextLines"
		};

		for(int i = 0; i < regexpProps.length; i++)
		{
			IndentRule rule = createRegexpIndentRule(regexpProps[i]);
			if(rule != null)
				indentRules.add(rule);
		}

		String[] bracketProps = {
			"indentOpenBracket",
			"indentCloseBracket",
			"unalignedOpenBracket",
			"unalignedCloseBracket",
		};

		for(int i = 0; i < bracketProps.length; i++)
		{
			indentRules.addAll(createBracketIndentRules(bracketProps[i]));
		}

		String[] finalProps = {
			"unindentThisLine",
			"unindentNextLines"
		};

		for(int i = 0; i < finalProps.length; i++)
		{
			IndentRule rule = createRegexpIndentRule(finalProps[i]);
			if(rule != null)
				indentRules.add(rule);
		}

		String[] props = {
			"indentOpenBrackets",
			"indentCloseBrackets",
			"electricKeys"
		};

		StringBuffer buf = new StringBuffer();
		for(int i = 0; i < props.length; i++)
		{
			String prop = getStringProperty(props[i]);
			if(prop != null)
				buf.append(prop);
		}

		electricKeys = buf.toString();
	} //}}}
	
	//{{{ Used to store property values
	protected static class PropValue
	{
		PropValue(Object value, boolean defaultValue)
		{
			if(value == null)
				throw new NullPointerException();
			this.value = value;
			this.defaultValue = defaultValue;
		}

		Object value;

		/**
		 * If this is true, then this value is cached from the mode
		 * or global defaults, so when the defaults change this property
		 * value must be reset.
		 */
		boolean defaultValue;

		/**
		 * For debugging purposes.
		 */
		public String toString()
		{
			return value.toString();
		}
	} //}}}

	//}}}
	
	//{{{ Private members
	private Vector bufferListeners;
	private ReadWriteLock lock;
	private ContentManager contentMgr;
	private LineManager lineMgr;
	private PositionManager positionMgr;
	private FoldHandler foldHandler;
	private IntegerArray integerArray;
	private TokenMarker tokenMarker;
	private boolean undoInProgress;
	private boolean dirty;
	private boolean readOnly;
	private boolean readOnlyOverride;
	private boolean transaction;
	private boolean loading;
	private boolean io;
	private HashMap properties;
	private Object propertyLock;
	private List indentRules;
	private String electricKeys;

	//{{{ getListener() method
	private BufferListener getListener(int index)
	{
		return ((Listener)bufferListeners.elementAt(index)).listener;
	} //}}}

	//{{{ contentInserted() method
	private void contentInserted(int offset, int length,
		IntegerArray endOffsets)
	{
		try
		{
			transaction = true;

			int startLine = lineMgr.getLineOfOffset(offset);
			int numLines = endOffsets.getSize();

			lineMgr.contentInserted(startLine,offset,numLines,length,
				endOffsets);
			positionMgr.contentInserted(offset,length);

			setDirty(true);

			if(!loading)
			{
				fireContentInserted(startLine,offset,numLines,length);

				if(!undoInProgress && !insideCompoundEdit())
					fireTransactionComplete();
			}

		}
		finally
		{
			transaction = false;
		}
	} //}}}

	//{{{ parseBufferLocalProperties() method
	private void parseBufferLocalProperties(String prop)
	{
		StringBuffer buf = new StringBuffer();
		String name = null;
		boolean escape = false;
		for(int i = 0; i < prop.length(); i++)
		{
			char c = prop.charAt(i);
			switch(c)
			{
			case ':':
				if(escape)
				{
					escape = false;
					buf.append(':');
					break;
				}
				if(name != null)
				{
					// use the low-level property setting code
					// so that if we have a buffer-local
					// property with the same value as a default,
					// later changes in the default don't affect
					// the buffer-local property
					properties.put(name,new PropValue(buf.toString(),false));
					name = null;
				}
				buf.setLength(0);
				break;
			case '=':
				if(escape)
				{
					escape = false;
					buf.append('=');
					break;
				}
				name = buf.toString();
				buf.setLength(0);
				break;
			case '\\':
				if(escape)
					buf.append('\\');
				escape = !escape;
				break;
			case 'n':
				if(escape)
				{	buf.append('\n');
					escape = false;
					break;
				}
			case 'r':
				if(escape)
				{	buf.append('\r');
					escape = false;
					break;
				}
			case 't':
				if(escape)
				{
					buf.append('\t');
					escape = false;
					break;
				}
			default:
				buf.append(c);
				break;
			}
		}
	} //}}}
	
	//{{{ createRegexpIndentRule() method
	private IndentRule createRegexpIndentRule(String prop)
	{
		String value = getStringProperty(prop);

		try
		{
			if(value != null)
			{
				Method m = IndentRuleFactory.class.getMethod(
					prop,new Class[] { String.class });
				return (IndentRule)m.invoke(null,
					new String[] { value });
			}
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,"Bad indent rule " + prop
				+ "=" + value + ":");
			Log.log(Log.ERROR,this,e);
		}

		return null;
	} //}}}

	//{{{ createBracketIndentRules() method
	private List createBracketIndentRules(String prop)
	{
		List returnValue = new ArrayList();
		String value = getStringProperty(prop + "s");

		try
		{
			if(value != null)
			{
				for(int i = 0; i < value.length(); i++)
				{
					char ch = value.charAt(i);

					Method m = IndentRuleFactory.class.getMethod(
						prop,new Class[] { char.class });
					returnValue.add(
						m.invoke(null,new Character[]
						{ new Character(ch) }));
				}
			}
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,"Bad indent rule " + prop
				+ "=" + value + ":");
			Log.log(Log.ERROR,this,e);
		}

		return returnValue;
	} //}}}

	//}}}
}
