/*
 * RESearchMatcher.java - Regular expression matcher
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1999, 2003 Slava Pestov
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
import bsh.BshMethod;
import bsh.NameSpace;
import gnu.regexp.*;
import org.gjt.sp.jedit.BeanShell;
import org.gjt.sp.jedit.MiscUtilities;
//}}}

/**
 * A regular expression string matcher using {@link gnu.regexp}.
 * @author Slava Pestov
 * @version $Id$
 */
public class RESearchMatcher extends SearchMatcher
{
	/**
	 * Perl5 syntax with character classes enabled.
	 * @since jEdit 3.0pre5
	 */
	public static final RESyntax RE_SYNTAX_JEDIT
		= new RESyntax(RESyntax.RE_SYNTAX_PERL5)
		.set(RESyntax.RE_CHAR_CLASSES)
		.setLineSeparator("\n");

	//{{{ RESearchMatcher constructor
	/**
	 * Creates a new regular expression string matcher.
	 * @since jEdit 4.2pre4
	 */
	public RESearchMatcher(String search, boolean ignoreCase)
		throws REException
	{
		re = new RE(search,(ignoreCase ? RE.REG_ICASE : 0)
			| RE.REG_MULTILINE,RE_SYNTAX_JEDIT);
		returnValue = new Match();
	} //}}}

	//{{{ nextMatch() method
	/**
	 * Returns the offset of the first match of the specified text
	 * within this matcher.
	 * @param text The text to search in
	 * @param start True if the start of the segment is the beginning of the
	 * buffer
	 * @param end True if the end of the segment is the end of the buffer
	 * @param firstTime If false and the search string matched at the start
	 * offset with length zero, automatically find next match
	 * @param reverse If true, searching will be performed in a backward
	 * direction.
	 * @return an array where the first element is the start offset
	 * of the match, and the second element is the end offset of
	 * the match
	 * @since jEdit 4.2pre4
	 */
	public SearchMatcher.Match nextMatch(CharIndexed text, boolean start,
		boolean end, boolean firstTime, boolean reverse)
	{
		int flags = 0;

		// unless we are matching from the start of the buffer,
		// ^ should not match on the beginning of the substring
		if(!start)
			flags |= RE.REG_NOTBOL;
		// unless we are matching to the end of the buffer,
		// $ should not match on the end of the substring
		if(!end)
			flags |= RE.REG_NOTEOL;

		REMatch match = re.getMatch(text,0,flags);
		if(match == null)
			return null;

		returnValue.substitutions = new String[
			re.getNumSubs() + 1];
		for(int i = 0; i < returnValue.substitutions.length; i++)
		{
			returnValue.substitutions[i] = match.toString(i);
		}

		int _start = match.getStartIndex();
		int _end = match.getEndIndex();

		// some regexps (eg ^ by itself) have a length == 0, so we
		// implement this hack. if you don't understand what's going on
		// here, then go back to watching MTV
		if(!firstTime && _start == 0 && _end == 0)
		{
			text.move(1);

			if(text.charAt(0) == CharIndexed.OUT_OF_BOUNDS)
			{
				// never mind
				return null;
			}

			match = re.getMatch(text,0,flags | RE.REG_NOTBOL);
			if(match == null)
				return null;
			else
			{
				_start = match.getStartIndex() + 1;
				_end = match.getEndIndex() + 1;
			}
		}

		returnValue.start = _start;
		returnValue.end = _end;
		return returnValue;
	} //}}}

	//{{{ Private members
	private RE re;
	//}}}
}
