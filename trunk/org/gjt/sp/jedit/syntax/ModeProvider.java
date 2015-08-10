/*
 * ModeProvider.java - An edit mode provider.
 * :tabSize=4:indentSize=4:noTabs=false:
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
package org.gjt.sp.jedit.syntax;

//{{{ Imports
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.Mode;
import org.gjt.sp.util.IOUtilities;
import org.gjt.sp.util.Log;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.regex.*;
import javax.swing.JOptionPane;
//}}}

/**
 * This class works like a singleton, the instance is initialized by jEdit.
 *
 * @author Matthieu Casanova
 * @version $Id: Buffer.java 8190 2006-12-07 07:58:34Z kpouer $
 * @since jEdit 4.3pre10
 */
public class ModeProvider
{
	public static ModeProvider instance = new ModeProvider();

	private final LinkedHashMap<String, Mode> modes = new LinkedHashMap<String, Mode>(220);

	//{{{ removeAll() method
	public void removeAll()
	{
		modes.clear();
	} //}}}

	//{{{ removeMode() method
	/**
 	 * Will only remove user modes.	
 	 */
	public void removeMode(String name)
	{
		Mode mode = modes.get(name);
		if (mode.isUserMode())
		{
			modes.remove(name);
			// delete mode file from disk and remove the entry from the catalog file.
			// Actually, just rename the mode file by adding "_unused" to the end of the file name
			// and comment out the line in the catalog file. This way it is possible to undo
			// these changes manually without too much work.
			String modeFilename = (String)mode.getProperty("file");
			File modeFile = new File(modeFilename);
			if (modeFile.exists()) 
			{
				try 
				{
					Path path = FileSystems.getDefault().getPath(modeFilename);
					Files.move(path, path.resolveSibling(modeFilename + "_unused"), StandardCopyOption.REPLACE_EXISTING);
				}
				catch(Exception e) {
					JOptionPane.showMessageDialog(jEdit.getActiveView(), 
						jEdit.getProperty("options.editing.deleteMode.dialog.message1") + " " + modeFilename + 
						"\n" + jEdit.getProperty("options.editing.deleteMode.dialog.message2") + " " + mode.getName());
					return;
				}
				
				// delete entry from mode catalog, catalog is in the same directory as the mode file
				File catalogFile = new File(modeFile.getParent(),"catalog");
				if (catalogFile.exists())
				{
					try 
					{
						// read in the catalog file
						BufferedReader br = new BufferedReader(new FileReader(catalogFile));
						String line = null;
						StringBuilder contents = new StringBuilder();
						while((line = br.readLine()) != null) {
							contents.append(line).append('\n');
						}
						br.close();
						
						// remove the catalog entry for this mode
						Pattern p = Pattern.compile("(?m)(^\\s*[<]MODE.*?NAME=\"" + name + "\".*?[>])");
						Matcher m = p.matcher(contents);
						String newContents = m.replaceFirst("<!--$1-->");
						
						// rewrite the catalog file
						BufferedWriter bw = new BufferedWriter(new FileWriter(catalogFile));
						bw.write(newContents, 0, newContents.length());
						bw.flush();
						bw.close();
					}
					catch(Exception e) 
					{
						// ignored 
					}
				}
			}
			
		}
	} //}}}

	//{{{ getMode() method
	/**
	 * Returns the edit mode with the specified name.
	 * @param name The edit mode
	 * @since jEdit 4.3pre10
	 */
	public Mode getMode(String name)
	{
		return modes.get(name);
	} //}}}

	//{{{ getModeForFile() method
	/**
	 * Get the appropriate mode that must be used for the file
	 * @param filename the filename
	 * @param firstLine the first line of the file
	 * @return the edit mode, or null if no mode match the file
	 * @since jEdit 4.3pre12
	 */
	public Mode getModeForFile(String filename, String firstLine)
	{
		return getModeForFile(null, filename, firstLine);
	} //}}}

	//{{{ getModeForFile() method
	/**
	 * Get the appropriate mode that must be used for the file
	 * @param filepath the filepath, can be {@code null}
	 * @param filename the filename, can be {@code null}
	 * @param firstLine the first line of the file
	 * @return the edit mode, or null if no mode match the file
	 * @since jEdit 4.5pre1
	 */
	public Mode getModeForFile(String filepath, String filename, String firstLine)
	{
		if (filepath != null && filepath.endsWith(".gz"))
			filepath = filepath.substring(0, filepath.length() - 3);
		if (filename != null && filename.endsWith(".gz"))
			filename = filename.substring(0, filename.length() - 3);

		List<Mode> acceptable = new ArrayList<Mode>(1);
		for(Mode mode : modes.values())
		{
			if(mode.accept(filepath, filename, firstLine))
			{
				acceptable.add(mode);
			}
		}
		if (acceptable.size() == 1)
		{
			return acceptable.get(0);
		}
		if (acceptable.size() > 1)
		{
			// The check should be in reverse order so that
			// modes from the user catalog get checked first!
			Collections.reverse(acceptable);

			// the very most acceptable mode is one whose file
			// name doesn't only match the file name as regular
			// expression but which is identical
			for (Mode mode : acceptable)
			{
				if (mode.acceptIdentical(filepath, filename))
				{
					return mode;
				}
			}

			// most acceptable is a mode that matches both the
			// filepath and the first line glob
			for (Mode mode : acceptable)
			{
				if (mode.acceptFile(filepath, filename) &&
					mode.acceptFirstLine(firstLine))
				{
					return mode;
				}
			}
			// next best is filepath match
			for (Mode mode : acceptable)
			{
				if (mode.acceptFile(filepath, filename)) {
					return mode;
				}
			}
			// all acceptable choices are by first line glob, and
			// they all match, so just return the first one.
			return acceptable.get(0);
		}
		// no matching mode found for this file
		return null;
	} //}}}

	//{{{ getModes() method
	/**
	 * Returns an array of installed edit modes.
	 * @since jEdit 4.3pre10
	 */
	public Mode[] getModes()
	{
		return modes.values().toArray(new Mode[modes.size()]);
	} //}}}

	//{{{ addMode() method
	/**
	 * Do not call this method. It is only public so that classes
	 * in the org.gjt.sp.jedit.syntax package can access it.
	 * @since jEdit 4.3pre10
	 * @see org.gjt.sp.jedit.jEdit#reloadModes reloadModes
	 * @param mode The edit mode
	 */
	public void addMode(Mode mode)
	{
		String name = mode.getName();

		// The removal makes the "insertion order" in modes
		// (LinkedHashMap) follow the order of addMode() calls.
		modes.remove(name);

		modes.put(name, mode);
	} //}}}

	//{{{ addUserMode() method
	/**
	 * Do not call this method. It is only public so that classes
	 * in the org.gjt.sp.jedit.syntax package can access it.
	 * @since jEdit 4.3pre10
	 * @see org.gjt.sp.jedit.jEdit#reloadModes reloadModes
	 * @param mode The edit mode
	 */
	public void addUserMode(Mode mode)
	{
		mode.setUserMode(true);
		String name = mode.getName();
		String modeFile = (String)mode.getProperty("file");
		String filenameGlob = (String)mode.getProperty("filenameGlob");
		String firstLineGlob = (String)mode.getProperty("firstlineGlob");
		
		// copy mode file to user mode directory
		Path target = null;
		try 
		{
			File file = new File(modeFile);
			Path source = FileSystems.getDefault().getPath(modeFile);
			target = FileSystems.getDefault().getPath(jEdit.getSettingsDirectory(), "modes", file.getName());
			target = Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
		catch(Exception e) {
			JOptionPane.showMessageDialog(jEdit.getActiveView(), jEdit.getProperty("options.editing.addMode.dialog.warning.message1") + " " + modeFile + "\n--> " + target);
			return;
		}
		
		// add entry to mode catalog, catalog is in the same directory as the mode file
		File catalogFile = new File(target.toFile().getParent(),"catalog");
		if (catalogFile.exists())
		{
			try 
			{
				// read in the catalog file
				BufferedReader br = new BufferedReader(new FileReader(catalogFile));
				String line = null;
				StringBuilder contents = new StringBuilder();
				while((line = br.readLine()) != null) {
					contents.append(line).append('\n');
				}
				br.close();
				
				// remove any existing catalog entry for this mode
				Pattern p = Pattern.compile("(?m)(^\\s*[<]MODE.*?NAME=\"" + name + "\".*?[>])");
				Matcher m = p.matcher(contents);
				String newContents = m.replaceFirst("<!--$1-->");
				
				// insert the catalog entry for this mode
				p = Pattern.compile("(?m)(</MODES>)");
				m = p.matcher(contents);
				StringBuilder modeLine = new StringBuilder("\t<MODE NAME=\"");
				modeLine.append(name).append("\" FILE=\"").append(target.toFile().getName()).append("\"");
				modeLine.append(filenameGlob == null || filenameGlob.isEmpty() ? "" : " FILE_NAME_GLOB=\"" + filenameGlob + "\"");
				modeLine.append(firstLineGlob == null || firstLineGlob.isEmpty() ? "" : " FIRST_LINE_GLOB=\"" + firstLineGlob + "\"");
				modeLine.append("/>");
				newContents = m.replaceFirst(modeLine + "\n$1" );
				
				// rewrite the catalog file
				BufferedWriter bw = new BufferedWriter(new FileWriter(catalogFile));
				bw.write(newContents, 0, newContents.length());
				bw.flush();
				bw.close();
			}
			catch(Exception e) 
			{
				// ignored 
			}
		}
		
		
		addMode(mode);
		loadMode(mode);
	} //}}}

	//{{{ loadMode() method
	public void loadMode(Mode mode, XModeHandler xmh)
	{
		String fileName = (String)mode.getProperty("file");

		Log.log(Log.NOTICE,this,"Loading edit mode " + fileName);

		XMLReader parser;
		try
		{
			parser = XMLReaderFactory.createXMLReader();
		} catch (SAXException saxe)
		{
			Log.log(Log.ERROR, this, saxe);
			return;
		}
		mode.setTokenMarker(xmh.getTokenMarker());

		InputStream grammar;

		try
		{
			grammar = new BufferedInputStream(
					new FileInputStream(fileName));
		}
		catch (FileNotFoundException e1)
		{
			InputStream resource = ModeProvider.class.getResourceAsStream(fileName);
			if (resource == null)
				error(fileName, e1);
			grammar = new BufferedInputStream(resource);
		}

		try
		{
			InputSource isrc = new InputSource(grammar);
			isrc.setSystemId("jedit.jar");
			parser.setContentHandler(xmh);
			parser.setDTDHandler(xmh);
			parser.setEntityResolver(xmh);
			parser.setErrorHandler(xmh);
			parser.parse(isrc);

			mode.setProperties(xmh.getModeProperties());
		}
		catch (Throwable e)
		{
			error(fileName, e);
		}
		finally
		{
			IOUtilities.closeQuietly(grammar);
		}
	} //}}}

	//{{{ loadMode() method
	public void loadMode(Mode mode)
	{
		XModeHandler xmh = new XModeHandler(mode.getName())
		{
			@Override
			public void error(String what, Object subst)
			{
				Log.log(Log.ERROR, this, subst);
			}

			@Override
			public TokenMarker getTokenMarker(String modeName)
			{
				Mode mode = getMode(modeName);
				if(mode == null)
					return null;
				else
					return mode.getTokenMarker();
			}
		};
		loadMode(mode, xmh);
	} //}}}

	//{{{ error() method
	protected void error(String file, Throwable e)
	{
		Log.log(Log.ERROR, this, e);
	} //}}}
}
