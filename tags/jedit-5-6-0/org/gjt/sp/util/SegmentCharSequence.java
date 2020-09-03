/*
 * SegmentCharSequence.java
 * :noTabs=false:
 *
 * Copyright (C) 2006 Marcelo Vanzin
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.gjt.sp.util;

import java.io.Serializable;
import javax.annotation.Nonnull;
import javax.swing.text.Segment;

/**
 * Class that lets java.util.regex search within a javax.swing.text.Segment.
 *
 * @author Marcelo Vanzin
 */
public class SegmentCharSequence implements CharSequence, Serializable
{
	public SegmentCharSequence(Segment seg)
	{
		this(seg, 0, seg.count);
	}

	public SegmentCharSequence(Segment seg, int off, int len)
	{
		this.offset = off;
		this.length = len;
		this.seg = seg;
	}

	@Override
	public char charAt(int index)
	{
		return seg.array[seg.offset + offset + index];
	}

	@Override
	public int length()
	{
		return length;
	}

	@Override
	public CharSequence subSequence(int start, int end)
	{
		return new SegmentCharSequence(seg, offset + start, end - start);
	}

	@Nonnull
	public String toString()
	{
		return new String(seg.array, offset+seg.offset, length);
	}

	private final int offset;
	private final int length;
	private final Segment seg;
}

