/*
 * IntegerArray.java - jEdit buffer
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2001 Slava Pestov
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

public class IntegerArray
{
	//{{{ IntegerArray constructor
	public IntegerArray()
	{
		array = new int[100];
	} //}}}

	//{{{ add() method
	public void add(int num)
	{
		if(len >= array.length)
		{
			int[] arrayN = new int[len * 2];
			System.arraycopy(array,0,arrayN,0,len);
			array = arrayN;
		}

		array[len++] = num;
	} //}}}

	//{{{ toArray() method
	public int[] toArray()
	{
		int[] retVal = new int[len];
		System.arraycopy(array,0,retVal,0,len);
		return retVal;
	} //}}}

	//{{{ Private members
	private int[] array;
	private int len;
	//}}}
}
