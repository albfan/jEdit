/*
 * BufferHistory.java - Remembers caret positions
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
import com.microstar.xml.*;
import java.io.*;
import java.util.*;
import org.gjt.sp.jedit.msg.DynamicMenuChanged;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.util.Log;
//}}}

/**
 * Recent file list.
 * @author Slava Pestov
 * @version $Id$
 */
public class BufferHistory
{
	//{{{ getEntry() method
	public static Entry getEntry(String path)
	{
		Iterator iter = history.iterator();
		while(iter.hasNext())
		{
			Entry entry = (Entry)iter.next();
			if(MiscUtilities.pathsEqual(entry.path,path))
				return entry;
		}

		return null;
	} //}}}

	//{{{ setEntry() method
	public static void setEntry(String path, int caret, Selection[] selection,
		String encoding)
	{
		removeEntry(path);
		addEntry(new Entry(path,caret,selectionToString(selection),
			encoding));
		EditBus.send(new DynamicMenuChanged("recent-files"));
	} //}}}

	//{{{ getHistory() method
	/**
	 * @since jEdit 4.2pre2
	 */
	public static List getHistory()
	{
		return history;
	} //}}}

	//{{{ load() method
	public static void load()
	{
		String settingsDirectory = jEdit.getSettingsDirectory();
		if(settingsDirectory == null)
			return;

		File recent = new File(MiscUtilities.constructPath(
			settingsDirectory,"recent.xml"));
		if(!recent.exists())
			return;

		recentModTime = recent.lastModified();

		Log.log(Log.MESSAGE,BufferHistory.class,"Loading recent.xml");

		RecentHandler handler = new RecentHandler();
		XmlParser parser = new XmlParser();
		Reader in = null;
		parser.setHandler(handler);
		try
		{
			in = new BufferedReader(new FileReader(recent));
			parser.parse(null, null, in);
		}
		catch(XmlException xe)
		{
			int line = xe.getLine();
			String message = xe.getMessage();
			Log.log(Log.ERROR,BufferHistory.class,recent + ":" + line
				+ ": " + message);
		}
		catch(FileNotFoundException fnf)
		{
			//Log.log(Log.DEBUG,BufferHistory.class,fnf);
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,BufferHistory.class,e);
		}
		finally
		{
			try
			{
				if(in != null)
					in.close();
			}
			catch(IOException io)
			{
				Log.log(Log.ERROR,BufferHistory.class,io);
			}
		}
	} //}}}

	//{{{ save() method
	public static void save()
	{
		String settingsDirectory = jEdit.getSettingsDirectory();
		if(settingsDirectory == null)
			return;

		File file1 = new File(MiscUtilities.constructPath(
			settingsDirectory, "#recent.xml#save#"));
		File file2 = new File(MiscUtilities.constructPath(
			settingsDirectory, "recent.xml"));
		if(file2.exists() && file2.lastModified() != recentModTime)
		{
			Log.log(Log.WARNING,BufferHistory.class,file2
				+ " changed on disk; will not save recent"
				+ " files");
			return;
		}

		jEdit.backupSettingsFile(file2);

		Log.log(Log.MESSAGE,BufferHistory.class,"Saving " + file1);

		String lineSep = System.getProperty("line.separator");

		boolean ok = false;

		BufferedWriter out = null;

		try
		{
			out = new BufferedWriter(new FileWriter(file1));

			out.write("<?xml version=\"1.0\"?>");
			out.write(lineSep);
			out.write("<!DOCTYPE RECENT SYSTEM \"recent.dtd\">");
			out.write(lineSep);
			out.write("<RECENT>");
			out.write(lineSep);

			Iterator iter = history.iterator();
			while(iter.hasNext())
			{
				out.write("<ENTRY>");
				out.write(lineSep);

				Entry entry = (Entry)iter.next();

				out.write("<PATH>");
				out.write(MiscUtilities.charsToEntities(entry.path));
				out.write("</PATH>");
				out.write(lineSep);

				out.write("<CARET>");
				out.write(String.valueOf(entry.caret));
				out.write("</CARET>");
				out.write(lineSep);

				if(entry.selection != null
					&& entry.selection.length() > 0)
				{
					out.write("<SELECTION>");
					out.write(entry.selection);
					out.write("</SELECTION>");
					out.write(lineSep);
				}

				if(entry.encoding != null)
				{
					out.write("<ENCODING>");
					out.write(entry.encoding);
					out.write("</ENCODING>");
					out.write(lineSep);
				}

				out.write("</ENTRY>");
				out.write(lineSep);
			}

			out.write("</RECENT>");
			out.write(lineSep);

			out.close();

			ok = true;
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,BufferHistory.class,e);
		}
		finally
		{
			try
			{
				if(out != null)
					out.close();
			}
			catch(IOException e)
			{
			}
		}

		if(ok)
		{
			/* to avoid data loss, only do this if the above
			 * completed successfully */
			file2.delete();
			file1.renameTo(file2);
		}

		recentModTime = file2.lastModified();
	} //}}}

	//{{{ Private members
	private static LinkedList history;
	private static long recentModTime;

	//{{{ Class initializer
	static
	{
		history = new LinkedList();
	} //}}}

	//{{{ addEntry() method
	/* private */ static void addEntry(Entry entry)
	{
		history.addFirst(entry);
		int max = jEdit.getIntegerProperty("recentFiles",50);
		while(history.size() > max)
			history.removeLast();
	} //}}}

	//{{{ removeEntry() method
	/* private */ static void removeEntry(String path)
	{
		Iterator iter = history.iterator();
		while(iter.hasNext())
		{
			Entry entry = (Entry)iter.next();
			if(MiscUtilities.pathsEqual(path,entry.path))
			{
				iter.remove();
				return;
			}
		}
	} //}}}

	//{{{ selectionToString() method
	private static String selectionToString(Selection[] s)
	{
		if(s == null)
			return null;

		StringBuffer buf = new StringBuffer();

		for(int i = 0; i < s.length; i++)
		{
			if(i != 0)
				buf.append(' ');

			Selection sel = s[i];
			if(sel instanceof Selection.Range)
				buf.append("range ");
			else //if(sel instanceof Selection.Rect)
				buf.append("rect ");
			buf.append(sel.getStart());
			buf.append(' ');
			buf.append(sel.getEnd());
		}

		return buf.toString();
	} //}}}

	//{{{ stringToSelection() method
	private static Selection[] stringToSelection(String s)
	{
		if(s == null)
			return null;

		Vector selection = new Vector();
		StringTokenizer st = new StringTokenizer(s);

		while(st.hasMoreTokens())
		{
			String type = st.nextToken();
			int start = Integer.parseInt(st.nextToken());
			int end = Integer.parseInt(st.nextToken());
			if(end < start)
			{
				// I'm not sure when this can happen,
				// but it does sometimes, witness the
				// jEdit bug tracker.
				continue;
			}

			Selection sel;
			if(type.equals("range"))
				sel = new Selection.Range(start,end);
			else //if(type.equals("rect"))
				sel = new Selection.Rect(start,end);

			selection.addElement(sel);
		}

		Selection[] returnValue = new Selection[selection.size()];
		selection.copyInto(returnValue);
		return returnValue;
	} //}}}

	//}}}

	//{{{ Entry class
	/**
	 * Recent file list entry.
	 */
	public static class Entry
	{
		public String path;
		public int caret;
		public String selection;
		public String encoding;

		public Selection[] getSelection()
		{
			return stringToSelection(selection);
		}

		public Entry(String path, int caret, String selection, String encoding)
		{
			this.path = path;
			this.caret = caret;
			this.selection = selection;
			this.encoding = encoding;
		}

		public String toString()
		{
			return path + ": " + caret;
		}
	} //}}}

	//{{{ RecentHandler class
	static class RecentHandler extends HandlerBase
	{
		public void endDocument()
			throws java.lang.Exception
		{
			int max = jEdit.getIntegerProperty("recentFiles",50);
			while(history.size() > max)
				history.removeLast();
		}

		public Object resolveEntity(String publicId, String systemId)
		{
			if("recent.dtd".equals(systemId))
			{
				// this will result in a slight speed up, since we
				// don't need to read the DTD anyway, as AElfred is
				// non-validating
				return new StringReader("<!-- -->");

				/* try
				{
					return new BufferedReader(new InputStreamReader(
						getClass().getResourceAsStream("recent.dtd")));
				}
				catch(Exception e)
				{
					Log.log(Log.ERROR,this,"Error while opening"
						+ " recent.dtd:");
					Log.log(Log.ERROR,this,e);
				} */
			}

			return null;
		}

		public void doctypeDecl(String name, String publicId,
			String systemId) throws Exception
		{
			if("RECENT".equals(name))
				return;

			Log.log(Log.ERROR,this,"recent.xml: DOCTYPE must be RECENT");
		}

		public void endElement(String name)
		{
			if(name.equals("ENTRY"))
			{
				history.addLast(new Entry(
					path,caret,selection,
					encoding));
				path = null;
				caret = 0;
				selection = null;
				encoding = null;
			}
			else if(name.equals("PATH"))
				path = charData;
			else if(name.equals("CARET"))
				caret = Integer.parseInt(charData);
			else if(name.equals("SELECTION"))
				selection = charData;
			else if(name.equals("ENCODING"))
				encoding = charData;
		}

		public void charData(char[] ch, int start, int length)
		{
			charData = new String(ch,start,length);
		}

		// end HandlerBase implementation

		// private members
		private String path;
		private int caret;
		private String selection;
		private String encoding;
		private String charData;
	} //}}}
}
