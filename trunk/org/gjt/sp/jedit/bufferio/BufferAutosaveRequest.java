/*
 * BufferAutosaveRequest.java - I/O request
 * :tabSize=4:indentSize=4:noTabs=false:
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

package org.gjt.sp.jedit.bufferio;

//{{{ Imports
import java.io.*;
import java.io.Closeable;

import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.*;
import org.gjt.sp.util.*;
//}}}

/**
 * A buffer autosave request.
 * @author Slava Pestov
 * @version $Id$
 */
public class BufferAutosaveRequest extends BufferIORequest
{
	//{{{ BufferAutosaveRequest constructor
	/**
	 * Creates a new buffer I/O request.
	 * @param view The view
	 * @param buffer The buffer
	 * @param session The VFS session
	 * @param vfs The VFS
	 * @param path The path
	 */
	public BufferAutosaveRequest(View view, Buffer buffer,
		Object session, VFS vfs, String path)
	{
		super(view,buffer,session,vfs,path);
	} //}}}

	//{{{ run() method
	public void _run()
	{
		OutputStream out = null;

		try
		{
			String[] args = { vfs.getFileName(path) };
			setStatus(jEdit.getProperty("vfs.status.autosave",args));

			// the entire save operation can be aborted...
			setCancellable(true);

			try
			{
				buffer.readLock();
				if(buffer.isDirty())
					out = vfs._createOutputStream(session,path,view);
				if(out != null)
					write(buffer,out);
			}
			catch (FileNotFoundException e)
			{
				Log.log(Log.WARNING,this,"Unable to save " + e.getMessage());
			}
			catch(InterruptedException e)
			{
				cleanUpIncomplete(out);
				Thread.currentThread().interrupt();
			}
			catch(Exception e)
			{
				Log.log(Log.ERROR,this,e);
				String[] pp = { e.toString() };
				VFSManager.error(view,path,"ioerror.write-error",pp);

				cleanUpIncomplete(out);
			}
			finally
			{
				buffer.readUnlock();
			}
		}
		finally
		{
			IOUtilities.closeQuietly((Closeable)out);
		}
	} //}}}

	private void cleanUpIncomplete(OutputStream out)
	{
		// Incomplete autosave file should not exist.
		if(out != null)
		{
			try
			{
				out.close();
				out = null;
				vfs._delete(session,path,view);
			}
			catch(IOException ioe)
			{
				Log.log(Log.ERROR,this,ioe);
			}
		}
	}
}
