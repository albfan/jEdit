/*
 * InstallThread.java
 *
 * Originally written by Slava Pestov for the jEdit installer project. This work
 * has been placed into the public domain. You may use this work in any way and
 * for any purpose you wish.
 *
 * THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND, NOT EVEN THE
 * IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR OF THIS SOFTWARE, ASSUMES
 * _NO_ RESPONSIBILITY FOR ANY CONSEQUENCE RESULTING FROM THE USE, MODIFICATION,
 * OR REDISTRIBUTION OF THIS SOFTWARE.
 */
package installer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Vector;

/*
 * The thread that performs installation.
 */
public class InstallThread extends Thread
{
	public InstallThread(Install installer, Progress progress,
		String installDir, OperatingSystem.OSTask[] osTasks,
		int size, Vector components)
	{
		super("Install thread");

		this.installer = installer;
		this.progress = progress;
		this.installDir = installDir;
		this.osTasks = osTasks;
		this.size = size;
		this.components = components;
	}

	public void run()
	{
		progress.setMaximum(size * 1024);

		//return value ignored : already signalled in ServerKiller
		progress.message("stopping any jEdit server");
		ServerKiller.quitjEditServer();

		try
		{
			// install user-selected packages
			for(int i = 0; i < components.size(); i++)
			{
				String comp = (String)components.elementAt(i);
				progress.message("Installing " + comp);
				installComponent(comp);
			}

			// do operating system specific stuff (creating startup
			// scripts, installing man pages, etc.)
			for(int i = 0; i < osTasks.length; i++)
			{
				progress.message("Performing task " +
					osTasks[i].getName());
				osTasks[i].perform(installDir,components);
			}
		}
		catch(FileNotFoundException fnf)
		{
			progress.error("The installer could not create the "
				+ "destination directory.\n"
				+ "Maybe you do not have write permission?");
			return;
		}
		catch(IOException io)
		{
			progress.error(io.toString());
			return;
		}

		progress.done();
	}

	// private members
	private Install installer;
	private Progress progress;
	private String installDir;
	private OperatingSystem.OSTask[] osTasks;
	private int size;
	private Vector components;

	private void installComponent(String name) throws IOException
	{
		InputStream in = new BufferedInputStream(
			getClass().getResourceAsStream(name + ".tar.bz2"));
		// skip header bytes
		// maybe should check if they're valid or not?
		in.read();
		in.read();

		TarInputStream tarInput = new TarInputStream(
			new CBZip2InputStream(in));
		TarEntry entry;
		String fileName = null;
		while((entry = tarInput.getNextEntry()) != null)
		{
			if(entry.isDirectory())
			{
				fileName = null;
				continue;
			}
			if (fileName == null)
			{
				fileName = entry.getName();
				if (fileName.equals("././@LongLink"))
				{
					fileName = new BufferedReader(new InputStreamReader(tarInput)).readLine();
					if(fileName == null)
					{
						// missing filename ??
						throw new IOException("Invalid or corrupt contents: file in tar with long filename but no filename found");
					}
					// bug #3837 - can't install because long file name ends with \0
					while(!fileName.isEmpty() && fileName.charAt(fileName.length()-1) == 0){
						fileName = fileName.substring(0, fileName.length()-1);
					}
					if(fileName.isEmpty())
					{
						// filename consists in '\0's ??
						throw new IOException("Invalid or corrupt contents: file in tar with long filename but empty filename found");
					}
					continue;
				}
			}
			//System.err.println(fileName);
			String outfile = installDir + File.separatorChar
				+ fileName.replace('/',File.separatorChar);
			installer.copy(tarInput,outfile,progress);
			fileName = null;
		}

		tarInput.close();
	}
}
