/*
 * AllBufferSet.java - All buffer matcher
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
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

package org.gjt.sp.jedit.search;

//{{{ Imports
import gnu.regexp.*;
import java.awt.Component;
import java.util.ArrayList;
import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;
//}}}

/**
 * A file set for searching all open buffers.
 * @author Slava Pestov
 * @version $Id$
 */
public class AllBufferSet extends BufferListSet
{
	//{{{ AllBufferSet constructor
	/**
	 * Creates a new all buffer set.
	 * @param glob The filename glob
	 * @since jEdit 2.7pre3
	 */
	public AllBufferSet(String glob)
	{
		this.glob = glob;
	} //}}}

	//{{{ getFileFilter() method
	/**
	 * Returns the filename filter.
	 * @since jEdit 2.7pre3
	 */
	public String getFileFilter()
	{
		return glob;
	} //}}}

	//{{{ getCode() method
	/**
	 * Returns the BeanShell code that will recreate this file set.
	 * @since jEdit 2.7pre3
	 */
	public String getCode()
	{
		return "new AllBufferSet(\"" + MiscUtilities.charsToEscapes(glob)
			+ "\")";
	} //}}}

	//{{{ Instance variables
	private String glob;
	//}}}

	//{{{ _getFiles() method
	protected String[] _getFiles(Component comp)
	{
		Buffer[] buffers = jEdit.getBuffers();
		ArrayList returnValue = new ArrayList(buffers.length);

		RE filter;
		try
		{
			filter = new RE(MiscUtilities.globToRE(glob));
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,e);
			return null;
		}

		for(int i = 0; i < buffers.length; i++)
		{
			Buffer buffer = buffers[i];
			if(filter.isMatch(buffer.getName()))
				returnValue.add(buffer.getPath());
		}

		return (String[])returnValue.toArray(new String[returnValue.size()]);
	} //}}}
}
