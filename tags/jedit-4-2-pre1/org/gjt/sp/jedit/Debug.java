/*
 * Debug.java - Various debugging flags
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2003 Slava Pestov
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

/**
 * This class contains various debugging flags mainly useful for core
 * development.
 * @since jEdit 4.2pre1
 * @author Slava Pestov
 * @version $Id$
 */
public class Debug
{
	/**
	 * Print messages when the gap moves, and other offset manager state
	 * changes.
	 */
	public static boolean OFFSET_DEBUG = false;

	/**
	 * Print messages when text area and display manager perform scroll
	 * updates.
	 */
	public static boolean SCROLL_DEBUG = false;

	/**
	 * Display an error if the scrolling code detects an inconsistency.
	 * This kills performance!
	 */
	public static boolean VERIFY_FIRST_LINE = false;

	/**
	 * Print messages when screen line counts change.
	 */
	public static boolean SCREEN_LINES_DEBUG = false;

	/**
	 * For checking context, etc.
	 */
	public static boolean TOKEN_MARKER_DEBUG = false;

	/**
	 * For checking fold level invalidation, etc.
	 */
	public static boolean FOLD_DEBUG = false;

	/**
	 * For checking invalidation, etc.
	 */
	public static boolean CHUNK_CACHE_DEBUG = false;

	/**
	 * Paints boxes around chunks.
	 */
	public static boolean CHUNK_PAINT_DEBUG = false;

	/**
	 * Show time taken to repaint text area painter.
	 */
	public static boolean PAINT_TIMER = false;

	/**
	 * Show time taken for each EBComponent.
	 */
	public static boolean EB_TIMER = false;

	/**
	 * Show time from receiving a key event and the resulting repaint.
	 */
	public static boolean KEY_DELAY_TIMER = false;

	/**
	 * Disable monospaced font optimization.
	 */
	public static boolean DISABLE_MONOSPACE_HACK = false;

	/**
	 * Paint strings instead of glyph vectors.
	 */
	public static boolean DISABLE_GLYPH_VECTOR = false;

	/**
	 * Logs messages when BeanShell code is evaluated.
	 */
	public static boolean BEANSHELL_DEBUG = false;
}