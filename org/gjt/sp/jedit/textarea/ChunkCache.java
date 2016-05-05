/*
 * ChunkCache.java - Intermediate layer between token lists from a TokenMarker
 * and what you see on screen
 * :tabSize=4:indentSize=4:noTabs=false:
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
import java.util.*;

import javax.swing.text.TabExpander;

import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.Debug;
import org.gjt.sp.jedit.syntax.*;
import org.gjt.sp.util.Log;
//}}}

/**
 * Manages low-level text display tasks - the visible lines in the TextArea.
 * The ChunkCache contains an array of LineInfo object.
 * Each LineInfo object is associated to one screen line of the TextArea and
 * contains informations about this line.
 * The array is resized when the TextArea geometry changes  
 *
 * @author Slava Pestov
 * @version $Id$
 */
class ChunkCache
{
	//{{{ ChunkCache constructor
	ChunkCache(TextArea textArea)
	{
		this.textArea = textArea;
		outFull = new ArrayList<Chunk>();
		outFullPhysicalLine = -1;
		tokenHandler = new DisplayTokenHandler();
	} //}}}

	//{{{ getMaxHorizontalScrollWidth() method
	/**
	 * Returns the max line width of the textarea.
	 * It will check all lines in the line info array.
	 *
	 * @return the max line width
	 */
	int getMaxHorizontalScrollWidth()
	{
		int max = 0;
		for(int i = 0; i < lineInfo.length; i++)
		{
			LineInfo info = lineInfo[i];
			if(info.width > max)
				max = info.width;
		}
		return max;
	} //}}}

	//{{{ getScreenLineOfOffset() method
	/**
	 * @param line physical line number of document 
	 * @param offset number of characters from the left of the line. 
	 * @return returns the screen line number where the line and offset are.
	 * It returns -1 if this position is not currently visible
	 */
	int getScreenLineOfOffset(int line, int offset)
	{
		if(lineInfo.length == 0)
			return -1;
		if(line < textArea.getFirstPhysicalLine())
			return -1;
		if(line == textArea.getFirstPhysicalLine()
			&& offset < getLineInfo(0).offset)
			return -1;
		if(line > textArea.getLastPhysicalLine())
			return -1;

		if(line == lastScreenLineP)
		{
			LineInfo last = getLineInfo(lastScreenLine);

			if(offset >= last.offset && offset < last.offset + last.length)
			{
				return lastScreenLine;
			}
		}

		int screenLine = -1;

		// Find the screen line containing this offset
		for(int i = 0, n = textArea.getVisibleLines(); i < n; i++)
		{
			LineInfo info = getLineInfo(i);
			if(info.physicalLine > line)
			{
				// line is invisible?
				return i - 1;
				//return -1;
			}
			if(info.physicalLine == line)
			{
				if(offset >= info.offset && offset < info.offset + info.length)
				{
					screenLine = i;
					break;
				}
			}
		}

		if(screenLine == -1)
			return -1;

		lastScreenLineP = line;
		lastScreenLine = screenLine;

		return screenLine;
	} //}}}

	//{{{ recalculateVisibleLines() method
	/**
	 * Recalculate visible lines.
	 * This is called when the TextArea geometry is changed or when the font is changed.
	 */
	void recalculateVisibleLines()
	{
		LineInfo[] newLineInfo = new LineInfo[textArea.getVisibleLines()];

		int start;
		if(lineInfo == null)
			start = 0;
		else
		{
			start = Math.min(lineInfo.length,newLineInfo.length);
			System.arraycopy(lineInfo,0,newLineInfo,0,start);
		}

		for(int i = start; i < newLineInfo.length; i++)
			newLineInfo[i] = new LineInfo();

		lineInfo = newLineInfo;

		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	//{{{ setBuffer() method
	void setBuffer(JEditBuffer buffer)
	{
		this.buffer = buffer;
		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	//{{{ scrollDown() method
	void scrollDown(int amount)
	{
		int visibleLines = textArea.getVisibleLines();

		System.arraycopy(lineInfo,amount,lineInfo,0,visibleLines - amount);

		for(int i = visibleLines - amount; i < visibleLines; i++)
		{
			lineInfo[i] = new LineInfo();
		}

		firstInvalidLine -= amount;
		if(firstInvalidLine < 0)
			firstInvalidLine = 0;

		if(Debug.CHUNK_CACHE_DEBUG)
		{
			System.err.println("f > t.f: only " + amount
				+ " need updates");
		}

		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	//{{{ scrollUp() method
	void scrollUp(int amount)
	{
		System.arraycopy(lineInfo,0,lineInfo,amount,
			textArea.getVisibleLines() - amount);

		for(int i = 0; i < amount; i++)
		{
			lineInfo[i] = new LineInfo();
		}

		// don't try this at home
		int oldFirstInvalidLine = firstInvalidLine;
		firstInvalidLine = 0;
		updateChunksUpTo(amount);
		firstInvalidLine = oldFirstInvalidLine + amount;
		if(firstInvalidLine > textArea.getVisibleLines())
			firstInvalidLine = textArea.getVisibleLines();

		if(Debug.CHUNK_CACHE_DEBUG)
		{
			Log.log(Log.DEBUG,this,"f > t.f: only " + amount
				+ " need updates");
		}

		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	//{{{ invalidateAll() method
	void invalidateAll()
	{
		firstInvalidLine = 0;
		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	public void reset()
	{
		invalidateAll();
		outFullPhysicalLine = -1;
		outFull.clear();
	}

	//{{{ invalidateChunksFromPhys() method
	void invalidateChunksFromPhys(int physicalLine)
	{
		if(physicalLine == outFullPhysicalLine)
			outFullPhysicalLine = -1;

		for(int i = 0; i < firstInvalidLine; i++)
		{
			LineInfo info = lineInfo[i];
			if(info.physicalLine == -1 || info.physicalLine >= physicalLine)
			{
				firstInvalidLine = i;
				if(i <= lastScreenLine)
					lastScreenLine = lastScreenLineP = -1;
				break;
			}
		}
	} //}}}

	//{{{ getLineInfo() method
	/**
	 * Returns the line informations for a given screen line or a non-null
	 * new LineInfo if the requested <code>screenLine</code> is out of range.
	 * @param screenLine the screen line
	 * @return the LineInfo for the screenLine
	 */
	LineInfo getLineInfo(int screenLine)
	{
		if (screenLine >= lineInfo.length)
			return new LineInfo();
		updateChunksUpTo(screenLine);
		return lineInfo[screenLine];
	} //}}}

	//{{{ getLineSubregionCount() method
	/**
	 * Returns the number of subregions of a physical line
	 * @param physicalLine a physical line
	 * @return the number of subregions of this physical line
	 */
	int getLineSubregionCount(int physicalLine)
	{
		if(!textArea.softWrap)
			return 1;

		lineToChunkList(physicalLine);

		int size = outFull.size();
		if(size == 0)
			return 1;
		else
			return size;
	} //}}}

	//{{{ getSubregionOfOffset() method
	/**
	 * Returns the subregion containing the specified offset. A subregion
	 * is a subset of a physical line. Each screen line corresponds to one
	 * subregion. Unlike the {@link #getScreenLineOfOffset(int, int)} method,
	 * this method works with non-visible lines too.
	 *
	 * @param offset the offset
	 * @param lineInfos a lineInfos array. Usualy the array is the result of
	 *	{@link #getLineInfosForPhysicalLine(int)} call
	 *
	 * @return the subregion of the offset, or -1 if the offset was not in one of the given lineInfos
	 */
	static int getSubregionOfOffset(int offset, LineInfo[] lineInfos)
	{
		for(int i = 0; i < lineInfos.length; i++)
		{
			LineInfo info = lineInfos[i];
			if(offset >= info.offset && offset < info.offset + info.length)
				return i;
		}

		return -1;
	} //}}}

	//{{{ xToSubregionOffset() method
	/**
	 * Converts an x co-ordinate within a subregion into an offset from the
	 * start of that subregion.
	 * @param physicalLine The physical line number
	 * @param subregion The subregion; if -1, then this is the last
	 * subregion.
	 * @param x The x co-ordinate
	 * @param round Round up to next character if x is past the middle of a
	 * character?
	 * @return the offset from the start of the subregion
	 */
	int xToSubregionOffset(int physicalLine, int subregion, int x,
		boolean round)
	{
		LineInfo[] infos = getLineInfosForPhysicalLine(physicalLine);
		if(subregion == -1)
			subregion += infos.length;
		return xToSubregionOffset(infos[subregion],x,round);
	} //}}}

	//{{{ xToSubregionOffset() method
	/**
	 * Converts an x co-ordinate within a subregion into an offset from the
	 * start of that subregion.
	 * @param info The line info object
	 * @param x The x co-ordinate
	 * @param round Round up to next character if x is past the middle of a
	 * character?
	 * @return the offset from the start of the subregion
	 */
	static int xToSubregionOffset(LineInfo info, int x,
		boolean round)
	{
		int offset = Chunk.xToOffset(info.chunks,x,round);
		if(offset == -1 || offset == info.offset + info.length)
			offset = info.offset + info.length - 1;

		return offset;
	} //}}}

	//{{{ subregionOffsetToX() method
	/**
	 * Converts an offset within a subregion into an x co-ordinate.
	 * @param physicalLine The physical line
	 * @param offset The offset
	 * @return the x co-ordinate of the offset within a subregion
	 */
	int subregionOffsetToX(int physicalLine, int offset)
	{
		LineInfo[] infos = getLineInfosForPhysicalLine(physicalLine);
		LineInfo info = infos[getSubregionOfOffset(offset,infos)];
		return subregionOffsetToX(info,offset);
	} //}}}

	//{{{ subregionOffsetToX() method
	/**
	 * Converts an offset within a subregion into an x co-ordinate.
	 * @param info The line info object
	 * @param offset The offset
	 * @return the x co-ordinate of the offset within a subregion
	 */
	static int subregionOffsetToX(LineInfo info, int offset)
	{
		return (int)Chunk.offsetToX(info.chunks,offset);
	} //}}}

	//{{{ getSubregionStartOffset() method
	/**
	 * Returns the start offset of the specified subregion of the specified
	 * physical line.
	 * @param line The physical line number
	 * @param offset An offset
	 * @return the start offset of the subregion of the line
	 */
	int getSubregionStartOffset(int line, int offset)
	{
		LineInfo[] lineInfos = getLineInfosForPhysicalLine(line);
		LineInfo info = lineInfos[getSubregionOfOffset(offset,lineInfos)];
		return textArea.getLineStartOffset(info.physicalLine)
			+ info.offset;
	} //}}}

	//{{{ getSubregionEndOffset() method
	/**
	 * Returns the end offset of the specified subregion of the specified
	 * physical line.
	 * @param line The physical line number
	 * @param offset An offset
	 * @return the end offset of the subregion of the line
	 */
	int getSubregionEndOffset(int line, int offset)
	{
		LineInfo[] lineInfos = getLineInfosForPhysicalLine(line);
		LineInfo info = lineInfos[getSubregionOfOffset(offset,lineInfos)];
		return textArea.getLineStartOffset(info.physicalLine)
			+ info.offset + info.length;
	} //}}}

	//{{{ getBelowPosition() method
	/**
	 * @param physicalLine The physical line number
	 * @param offset The offset
	 * @param x The location
	 * @param ignoreWrap If true, behave as if soft wrap is off even if it
	 * is on
	 */
	int getBelowPosition(int physicalLine, int offset, int x,
		boolean ignoreWrap)
	{
		LineInfo[] lineInfos = getLineInfosForPhysicalLine(physicalLine);

		int subregion = getSubregionOfOffset(offset,lineInfos);

		if(subregion != lineInfos.length - 1 && !ignoreWrap)
		{
			return textArea.getLineStartOffset(physicalLine)
				+ xToSubregionOffset(lineInfos[subregion + 1],
				x,true);
		}
		else
		{
			int nextLine = textArea.displayManager
				.getNextVisibleLine(physicalLine);

			if(nextLine == -1)
				return -1;
			else
			{
				return textArea.getLineStartOffset(nextLine)
					+ xToSubregionOffset(nextLine,0,
					x,true);
			}
		}
	} //}}}

	//{{{ getAbovePosition() method
	/**
	 * @param physicalLine The physical line number
	 * @param offset The offset
	 * @param x The location
	 * @param ignoreWrap If true, behave as if soft wrap is off even if it
	 * is on
	 */
	int getAbovePosition(int physicalLine, int offset, int x,
		boolean ignoreWrap)
	{
		LineInfo[] lineInfos = getLineInfosForPhysicalLine(physicalLine);

		int subregion = getSubregionOfOffset(offset,lineInfos);

		if(subregion != 0 && !ignoreWrap)
		{
			return textArea.getLineStartOffset(physicalLine)
				+ xToSubregionOffset(lineInfos[subregion - 1],
				x,true);
		}
		else
		{
			int prevLine = textArea.displayManager
				.getPrevVisibleLine(physicalLine);

			if(prevLine == -1)
				return -1;
			else
			{
				return textArea.getLineStartOffset(prevLine)
					+ xToSubregionOffset(prevLine,-1,
					x,true);
			}
		}
	} //}}}

	//{{{ needFullRepaint() method
	/**
	 * The needFullRepaint variable becomes true when the number of screen
	 * lines in a physical line changes.
	 * @return true if the TextArea needs full repaint
	 */
	boolean needFullRepaint()
	{
		boolean retVal = needFullRepaint;
		needFullRepaint = false;
		return retVal;
	} //}}}

	//{{{ getLineInfosForPhysicalLine() method
	LineInfo[] getLineInfosForPhysicalLine(int physicalLine)
	{
		if(!buffer.isLoading())
			lineToChunkList(physicalLine);

		assert physicalLine == outFullPhysicalLine;

		List<Chunk> chunkList = null;
		if(outFull.isEmpty()) {
			chunkList = new ArrayList<Chunk>();
			chunkList.add(null);
		} else
			chunkList = outFull;

		List<LineInfo> returnValue = new ArrayList<LineInfo>(chunkList.size());
		getLineInfosForPhysicalLine(physicalLine,returnValue, chunkList);
		return returnValue.toArray(new LineInfo[chunkList.size()]);
	} //}}}

	//{{{ Private members

	//{{{ Instance variables
	private final TextArea textArea;
	private JEditBuffer buffer;
	/**
	 * The lineInfo array. There is LineInfo for each line that is visible in the textArea.
	 * it can be resized by {@link #recalculateVisibleLines()}.
	 * The content is valid from 0 to {@link #firstInvalidLine}
	 */
	private LineInfo[] lineInfo;

	/**
	 * All Chunks for the current physical line, see also {@link #outFullPhysicalLine}
	 * The Chunks were created via a call to {@link #lineToChunkList(int)}.
	 */
	private final List<Chunk> outFull;

	/**
	 * Physical line number of the current tokenized/chunked line
	 * This field contains -1 if the line was not tokenized yet, or
	 * if the outFull cache was dropped via a {@link #reset()} call
	 * See also {@link #outFull}
	 */
	private int outFullPhysicalLine;

	/** The first invalid line. All lines before this one are valid. */
	private int firstInvalidLine;
	private int lastScreenLineP;
	private int lastScreenLine;

	private boolean needFullRepaint;

	private final DisplayTokenHandler tokenHandler;
	//}}}

	//{{{ getLineInfosForPhysicalLine() method
	private void getLineInfosForPhysicalLine(int physicalLine, List<LineInfo> list, List<Chunk> chunkList)
	{
		assert outFullPhysicalLine == physicalLine;

		for(int i = 0; i < chunkList.size(); i++)
		{
			Chunk chunks = chunkList.get(i);
			LineInfo info = new LineInfo();
			info.physicalLine = physicalLine;
			if(i == 0)
			{
				info.firstSubregion = true;
				info.offset = 0;
			}
			else
				info.offset = chunks.offset;

			if(i == chunkList.size() - 1)
			{
				info.lastSubregion = true;
				info.length = textArea.getLineLength(physicalLine)
					- info.offset + 1;
			}
			else
			{
				info.length = outFull.get(i + 1).offset
					- info.offset;
			}

			info.chunks = chunks;

			list.add(info);
		}
	} //}}}

	//{{{ getFirstScreenLine() method
	/**
	 * Find a valid line closest to the last screen line.
	 */
	private int getFirstScreenLine()
	{
		for(int i = firstInvalidLine - 1; i >= 0; i--)
		{
			if(lineInfo[i].lastSubregion)
				return i + 1;
		}

		return 0;
	} //}}}

	//{{{ getUpdateStartLine() method
	/**
	 * Return a physical line number.
	 */
	private int getUpdateStartLine(int firstScreenLine)
	{
		// for the first line displayed, take its physical line to be
		// the text area's first physical line
		if(firstScreenLine == 0)
		{
			return textArea.getFirstPhysicalLine();
		}
		// otherwise, determine the next visible line
		else
		{
			int prevPhysLine = lineInfo[
				firstScreenLine - 1]
				.physicalLine;
			// if -1, the empty space at the end of the text area
			// when the buffer has less lines than are visible
			if(prevPhysLine == -1)
				return -1;
			else
			{
				return textArea.displayManager
					.getNextVisibleLine(prevPhysLine);
			}
		}
	} //}}}

	//{{{ updateChunksUpTo() method
	private void updateChunksUpTo(int lastScreenLine)
	{
		// this method is a nightmare
		if(lastScreenLine >= lineInfo.length)
			throw new ArrayIndexOutOfBoundsException(lastScreenLine);

		// if one line's chunks are invalid, remaining lines are also
		// invalid
		// i.e. if the lastScreenLine is smaller as the first invalid
		// screen line we don't need to update the chunks, leave here
		if(lastScreenLine < firstInvalidLine)
			return;

		int firstScreenLine = getFirstScreenLine();
		int physicalLine = getUpdateStartLine(firstScreenLine);

		if(Debug.CHUNK_CACHE_DEBUG)
		{
			Log.log(Log.DEBUG,this,"Updating chunks from " + firstScreenLine
				+ " to " + lastScreenLine);
		}

		// Below comment is not true any more (at least partly):
		// Note that we rely on the fact that when a physical line is
		// invalidated, all screen lines/subregions of that line are
		// invalidated as well. See below comment for code that tries
		// to uphold this assumption.
		List<Chunk> out = new ArrayList<Chunk>(0);

		int offset;
		int length;

		for(int i = firstScreenLine; i <= lastScreenLine; i++)
		{
			LineInfo info = lineInfo[i];

			Chunk chunks;

			// get another line of chunks
			if(out.isEmpty())
			{
				// unless this is the first time, increment
				// the line number
				if(physicalLine != -1 && i != firstScreenLine)
				{
					physicalLine = textArea.displayManager
						.getNextVisibleLine(physicalLine);
				}

				// empty space
				if(physicalLine == -1)
				{
					info.chunks = null;
					info.physicalLine = -1;
					// fix the bug where the horiz.
					// scroll bar was not updated
					// after creating a new file.
					info.width = 0;
					continue;
				}

				// chunk the line.
				lineToChunkList(physicalLine);
				out = outFull.subList(0, outFull.size());

				info.firstSubregion = true;

				// if the line has no text, out.size() == 0
				if(out.isEmpty())
				{
					if(i == 0)
					{
						if(textArea.displayManager.firstLine.getSkew() > 0)
						{
							Log.log(Log.ERROR,this,"BUG: skew=" + textArea.displayManager.firstLine.getSkew() + ",out.size()=" + out.size());
							textArea.displayManager.firstLine.setSkew(0);
							needFullRepaint = true;
							lastScreenLine = lineInfo.length - 1;
						}
					}
					chunks = null;
					offset = 0;
					length = 1;
				}
				// otherwise, the number of subregions
				else
				{
					if(i == 0) //FIXME: (i == 0)!? - not (i == firstLine)?
					{
						int skew = textArea.displayManager.firstLine.getSkew();
						if(skew >= out.size())
						{
							// The skew cannot be greater than the chunk count of the line
							// we need at least one chunk per subregion in a line 
							Log.log(Log.ERROR,this,"BUG: skew=" + skew + ",out.size()=" + out.size());
							needFullRepaint = true;
							lastScreenLine = lineInfo.length - 1;
						}
						else if(skew > 0)
						{
							info.firstSubregion = false;
							out = outFull.subList(skew, outFull.size());
						}
					}
					chunks = out.get(0);
					out = out.subList(1, out.size());
					offset = chunks.offset;
					if (!out.isEmpty())
						length = out.get(0).offset - offset;
					else
						length = textArea.getLineLength(physicalLine) - offset + 1;
				}
			}
			else
			{
				info.firstSubregion = false;

				chunks = out.get(0);
				out = out.subList(1, out.size());
				offset = chunks.offset;
				if (!out.isEmpty())
					length = out.get(0).offset - offset;
				else
					length = textArea.getLineLength(physicalLine) - offset + 1;
			}

			boolean lastSubregion = out.isEmpty();

			if(i == lastScreenLine && lastScreenLine != lineInfo.length - 1)
			{
				/* if the user changes the syntax token at the
				 * end of a line, need to do a full repaint. */
				if(tokenHandler.getLineContext() !=
					info.lineContext)
				{
					lastScreenLine++;
					needFullRepaint = true;
				}
				/* If this line has become longer or shorter
				 * (in which case the new physical line number
				 * is different from the cached one) we need to:
				 * - continue updating past the last line
				 * - advise the text area to repaint
				 * On the other hand, if the line wraps beyond
				 * lastScreenLine, we need to keep updating the
				 * chunk list to ensure proper alignment of
				 * invalidation flags (see start of method) */
				else if(info.physicalLine != physicalLine
					|| info.lastSubregion != lastSubregion)
				{
					lastScreenLine++;
					needFullRepaint = true;
				}
				/* We only cache entire physical lines at once;
				 * don't want to split a physical line into
				 * screen lines and only have some valid. */
				else if (!out.isEmpty())
					lastScreenLine++;
			}

			info.physicalLine = physicalLine;
			info.lastSubregion = lastSubregion;
			info.offset = offset;
			info.length = length;
			info.chunks = chunks;
			info.lineContext = tokenHandler.getLineContext();
		}

		firstInvalidLine = Math.max(lastScreenLine + 1,firstInvalidLine);
	} //}}}

	//{{{ lineToChunkList() method
	/** chunks the line and fill outFull array with the chunks for the given physical line */
	private void lineToChunkList(int physicalLine)
	{
		assert physicalLine >= 0;

		if(outFullPhysicalLine != physicalLine) {
			TextAreaPainter painter = textArea.getPainter();
			TabExpander expander= textArea.getTabExpander();
			tokenHandler.init(painter.getStyles(),
					painter.getFontRenderContext(),
					expander,outFull,
					textArea.softWrap
					? textArea.wrapMargin : 0.0f, buffer.getLineStartOffset(physicalLine));

			outFull.clear();
			buffer.markTokens(physicalLine,tokenHandler);
			outFullPhysicalLine = physicalLine;
		}
	} //}}}

	//}}}

	//{{{ LineInfo class
	/**
	 * The informations on a line. (for fast access)
	 * When using softwrap, a line is divided in n
	 * subregions.
	 */
	static class LineInfo
	{
		/**
		 * The physical line.
		 */
		int physicalLine = -1;
		/**
		 * The offset where begins the line.
		 */
		int offset = 0;;
		/**
		 * The line length.
		 */
		int length = 0;
		/**
		 * true if it is the first subregion of a line.
		 */
		boolean firstSubregion;
		/**
		 * True if it is the last subregion of a line.
		 */
		boolean lastSubregion;
		Chunk chunks = null;
		/** The line width. */
		int width = 0;
		TokenMarker.LineContext lineContext = null;

		@Override
		public String toString()
		{
			return "LineInfo[" + physicalLine + ',' + offset + ','
			       + length + ',' + firstSubregion + ',' +
			       lastSubregion + "]";
		}
	} //}}}
}
