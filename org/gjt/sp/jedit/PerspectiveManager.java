/*
 * PerspectiveManager.java - Saves view configuration
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

import com.microstar.xml.*;
import java.io.*;
import org.gjt.sp.util.Log;

/**
 * Manages persistence of open buffers and views across jEdit sessions.
 * @since jEdit 4.2pre1
 * @author Slava Pestov
 * @version $Id$
 */
public class PerspectiveManager
{
	//{{{ isPerspectiveDirty() method
	/**
	 * We only autosave the perspective if it has changed, to avoid spinning
	 * up the disk on laptops.
	 * @since jEdit 4.2pre13
	 */
	public static boolean isPerspectiveDirty()
	{
		return dirty;
	} //}}}

	//{{{ setPerspectiveDirty() method
	/**
	 * We only autosave the perspective if it has changed, to avoid spinning
	 * up the disk on laptops.
	 * @since jEdit 4.2pre13
	 */
	public static void setPerspectiveDirty(boolean dirty)
	{
		PerspectiveManager.dirty = dirty;
	} //}}}

	//{{{ isPerspectiveEnabled() method
	/**
	 * We disable saving of the perspective while the 'close all' dialog is
	 * showing.
	 * @since jEdit 4.3pre2
	 */
	public static boolean isPerspectiveEnabled()
	{
		return enabled;
	} //}}}

	//{{{ setPerspectiveEnabled() method
	/**
	 * We disable saving of the perspective while the 'close all' dialog is
	 * showing.
	 * @since jEdit 4.3pre2
	 */
	public static void setPerspectiveEnabled(boolean enabled)
	{
		PerspectiveManager.enabled = enabled;
	} //}}}

	//{{{ loadPerspective() method
	public static View loadPerspective(boolean restoreFiles)
	{
		String settingsDirectory = jEdit.getSettingsDirectory();
		if(settingsDirectory == null)
			return null;

		File perspective = new File(MiscUtilities.constructPath(
			settingsDirectory,"perspective.xml"));

		if(!perspective.exists())
			return null;

		Log.log(Log.MESSAGE,PerspectiveManager.class,"Loading " + perspective);

		PerspectiveHandler handler = new PerspectiveHandler(restoreFiles);
		XmlParser parser = new XmlParser();
		parser.setHandler(handler);
		Reader in = null;
		try
		{
			in = new BufferedReader(new FileReader(perspective));
			parser.parse(null, null, in);
		}
		catch(XmlException xe)
		{
			int line = xe.getLine();
			String message = xe.getMessage();
			Log.log(Log.ERROR,PerspectiveManager.class,perspective
				+ ":" + line + ": " + message);
		}
		catch(FileNotFoundException fnf)
		{
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,PerspectiveManager.class,e);
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
				Log.log(Log.ERROR,PerspectiveManager.class,io);
			}
		}

		return handler.view;
	} //}}}

	//{{{ savePerspective() method
	public static void savePerspective(boolean autosave)
	{
		if(!isPerspectiveEnabled())
			return;
		
		String settingsDirectory = jEdit.getSettingsDirectory();
		if(settingsDirectory == null)
			return;

		// backgrounded
		if(jEdit.getBufferCount() == 0)
			return;

		if(!autosave)
			Log.log(Log.MESSAGE,PerspectiveManager.class,"Saving perspective.xml");

		File file1 = new File(MiscUtilities.constructPath(
			settingsDirectory,"#perspective.xml#save#"));
		File file2 = new File(MiscUtilities.constructPath(
			settingsDirectory,"perspective.xml"));

		String lineSep = System.getProperty("line.separator");

		BufferedWriter out = null;

		try
		{
			out = new BufferedWriter(new FileWriter(file1));

			out.write("<?xml version=\"1.0\"?>");
			out.write(lineSep);
			out.write("<!DOCTYPE PERSPECTIVE SYSTEM \"perspective.dtd\">");
			out.write(lineSep);
			out.write("<PERSPECTIVE>");
			out.write(lineSep);

			Buffer[] buffers = jEdit.getBuffers();
			for(int i = 0; i < buffers.length; i++)
			{
				Buffer buffer = buffers[i];
				if(buffer.isNewFile())
					continue;
				out.write("<BUFFER>");
				out.write(MiscUtilities.charsToEntities(buffer.getPath()));
				out.write("</BUFFER>");
				out.write(lineSep);
			}

			View[] views = jEdit.getViews();
			for(int i = 0; i < views.length; i++)
			{
				View view = views[i];
				// ensures that active view is saved last,
				// ie created last on next load, ie in front
				// on next load
				if(view == jEdit.getActiveView()
					&& i != views.length - 1)
				{
					View last = views[views.length - 1];
					views[i] = last;
					views[views.length - 1] = view;
					view = last;
				}

				View.ViewConfig config = views[i].getViewConfig();
				out.write("<VIEW PLAIN=\"");
				out.write(config.plainView ? "TRUE" : "FALSE");
				out.write("\">");

				out.write("<PANES>");
				out.write(lineSep);
				out.write(MiscUtilities.charsToEntities(
					config.splitConfig));
				out.write(lineSep);
				out.write("</PANES>");
				out.write(lineSep);

				out.write("<GEOMETRY X=\"");
				out.write(String.valueOf(config.x));
				out.write("\" Y=\"");
				out.write(String.valueOf(config.y));
				out.write("\" WIDTH=\"");
				out.write(String.valueOf(config.width));
				out.write("\" HEIGHT=\"");
				out.write(String.valueOf(config.height));
				out.write("\" EXT_STATE=\"");
				out.write(String.valueOf(config.extState));
				out.write("\" />");
				out.write(lineSep);

				out.write("<DOCKING LEFT=\"");
				out.write(config.left == null ? "" : config.left);
				out.write("\" TOP=\"");
				out.write(config.top == null ? "" : config.top);
				out.write("\" RIGHT=\"");
				out.write(config.right == null ? "" : config.right);
				out.write("\" BOTTOM=\"");
				out.write(config.bottom == null ? "" : config.bottom);
				out.write("\" LEFT_POS=\"");
				out.write(String.valueOf(config.leftPos));
				out.write("\" TOP_POS=\"");
				out.write(String.valueOf(config.topPos));
				out.write("\" RIGHT_POS=\"");
				out.write(String.valueOf(config.rightPos));
				out.write("\" BOTTOM_POS=\"");
				out.write(String.valueOf(config.bottomPos));
				out.write("\" />");
				out.write(lineSep);

				out.write("</VIEW>");
				out.write(lineSep);
			}

			out.write("</PERSPECTIVE>");
			out.write(lineSep);
		}
		catch(IOException io)
		{
			Log.log(Log.ERROR,PerspectiveManager.class,"Error saving " + file1);
			Log.log(Log.ERROR,PerspectiveManager.class,io);
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

		file2.delete();
		file1.renameTo(file2);
	} //}}}

	private static boolean dirty, enabled = true;

	//{{{ PerspectiveHandler class
	static class PerspectiveHandler extends HandlerBase
	{
		View view;
		String charData;
		View.ViewConfig config;
		boolean restoreFiles;

		PerspectiveHandler(boolean restoreFiles)
		{
			this.restoreFiles = restoreFiles;
			config = new View.ViewConfig();
		}

		public Object resolveEntity(String publicId, String systemId)
		{
			if("perspective.dtd".equals(systemId))
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
			if("PERSPECTIVE".equals(name))
				return;

			Log.log(Log.ERROR,this,"perspective.xml: DOCTYPE must be PERSPECTIVE");
		}

		public void attribute(String aname, String value, boolean specified)
		{
			if(!specified)
				return;

			if(aname.equals("X"))
				config.x = Integer.parseInt(value);
			else if(aname.equals("Y"))
				config.y = Integer.parseInt(value);
			else if(aname.equals("WIDTH"))
				config.width = Integer.parseInt(value);
			else if(aname.equals("HEIGHT"))
				config.height = Integer.parseInt(value);
			else if(aname.equals("EXT_STATE"))
				config.extState = Integer.parseInt(value);
			else if(aname.equals("PLAIN"))
				config.plainView = ("TRUE".equals(value));
			else if(aname.equals("TOP"))
				config.top = value;
			else if(aname.equals("LEFT"))
				config.left = value;
			else if(aname.equals("BOTTOM"))
				config.bottom = value;
			else if(aname.equals("RIGHT"))
				config.right = value;
			else if(aname.equals("TOP_POS"))
				config.topPos = Integer.parseInt(value);
			else if(aname.equals("LEFT_POS"))
				config.leftPos = Integer.parseInt(value);
			else if(aname.equals("BOTTOM_POS"))
				config.bottomPos = Integer.parseInt(value);
			else if(aname.equals("RIGHT_POS"))
				config.rightPos = Integer.parseInt(value);
		}

		public void endElement(String name)
		{
			if(name.equals("BUFFER"))
			{
				if(restoreFiles)
					jEdit.openFile(null,charData);
			}
			else if(name.equals("PANES"))
				config.splitConfig = charData;
			else if(name.equals("VIEW"))
			{
				view = jEdit.newView(view,null,config);
				config = new View.ViewConfig();
			}
		}

		public void charData(char[] ch, int start, int length)
		{
			charData = new String(ch,start,length);
		}
	} //}}}
}
