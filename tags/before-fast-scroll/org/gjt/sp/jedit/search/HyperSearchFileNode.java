/*
 * HyperSearchFileNode.java - HyperSearch file node
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2005 Slava Pestov
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

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.jEdit;

public class HyperSearchFileNode implements HyperSearchNode
{
	public String path;
	public Buffer buffer;

	//{{{ HyperSearchFileNode constructor
	public HyperSearchFileNode(String path)
	{
		this.path = path;
	} //}}}

	//{{{ getBuffer() method
	public Buffer getBuffer()
	{
		if(buffer == null)
			buffer = jEdit.openFile(null,path);
		return buffer;
	} //}}}

	//{{{ goTo() method
	public void goTo(final EditPane editPane)
	{
		Buffer buffer = getBuffer();
		if(buffer == null)
			return;

		editPane.setBuffer(buffer);
	} //}}}
	
	//{{{ toString() method
	public String toString()
	{
		return path;
	} //}}}
}
