/*
 * BufferPrinter1_7.java - Main class that controls printing
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2001 Slava Pestov
 * Portions copyright (C) 2002 Thomas Dilts
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
package org.gjt.sp.jedit.print;


import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.util.HashMap;

import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import javax.print.event.*;
import javax.swing.JOptionPane;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;


public class BufferPrinter1_7
{

	/**
	 * Shows the printer dialog with the page setup tab active, other tabs inactive.
	 * @param view The parent view for the dialog.
	 */
	public static void pageSetup( View view )
	{
		loadPrintSpec();
		PrinterDialog printerDialog = new PrinterDialog( view, format, true );
		if ( !printerDialog.isCanceled() )
		{
			format = printerDialog.getAttributes();
			savePrintSpec();
			EditBus.send(new PropertiesChanged(null));
		}
	}	


	public static void print( final View view, final Buffer buffer )
	{

		// load any saved printing attributes, these are put into 'format'
		loadPrintSpec();
		String jobName = MiscUtilities.abbreviateView( buffer.getPath() );
		format.add( new JobName( jobName, null ) );

		// show the print dialog so the user can make their printer settings
		PrinterDialog printerDialog = new PrinterDialog( view, format, false );
		if ( printerDialog.isCanceled() )
		{
			return;
		}


		// set up the print job
		PrintService printService = printerDialog.getPrintService();
		if ( printService != null )
		{
			try

			{
				job = printService.createPrintJob();
				job.addPrintJobListener( new BufferPrinter1_7.JobListener( view ) );
				format = printerDialog.getAttributes();
				savePrintSpec();
				EditBus.send(new PropertiesChanged(null));
			}
			catch ( Exception e )
			{
				JOptionPane.showMessageDialog( view, jEdit.getProperty( "print-error.message", new String[] {e.getMessage()} ), jEdit.getProperty( "print-error.title" ), JOptionPane.ERROR_MESSAGE );
				return;
			}
		}
		else
		{
			JOptionPane.showMessageDialog( view, jEdit.getProperty( "print-error.message", new String[] {"Invalid print service."} ), jEdit.getProperty( "print-error.title" ), JOptionPane.ERROR_MESSAGE );
			return;
		}


		// set up the printable. Some values need to be set directly from the print
		// dialog since they don't have attributes, like reverse page printing and printRangeType
		BufferPrintable1_7 printable = new BufferPrintable1_7( format, view, buffer );
		printable.setReverse( printerDialog.getReverse() );
		int printRangeType = printerDialog.getPrintRangeType();
		printable.setPrintRangeType( printRangeType );

		// check if printing a selection, if so, recalculate the page ranges.
		// TODO: I'm not taking even/odd page setting into account here, nor am
		// I considering any page values that may have been set in the page range.
		// I don't think this is important for printing a selection, which is
		// generally just a few lines rather than pages. I could be wrong...
		if ( printRangeType == PrinterDialog.SELECTION )
		{

			// calculate the actual pages with a selection or bail if there is no selection
			int selectionCount = view.getTextArea().getSelectionCount();
			if ( selectionCount == 0 )
			{
				JOptionPane.showMessageDialog( view, jEdit.getProperty( "print-error.message", new String[] {"No text is selected to print."} ), jEdit.getProperty( "print-error.title" ), JOptionPane.ERROR_MESSAGE );
				return;
			}


			// get the page ranges from the printable
			HashMap<Integer, Range> pageRanges = getPageRanges( printable, format );
			if ( pageRanges == null || pageRanges.isEmpty() )
			{
				JOptionPane.showMessageDialog( view, jEdit.getProperty( "print-error.message", new String[] {"Unable to calculate page ranges."} ), jEdit.getProperty( "print-error.title" ), JOptionPane.ERROR_MESSAGE );
				return;
			}


			// find the pages that contain the selection(s) and construct a new
			// page range for the format
			int[] selectedLines = view.getTextArea().getSelectedLines();
			StringBuilder pageRange = new StringBuilder();
			for ( Integer i : pageRanges.keySet() )
			{
				Range range = pageRanges.get( i );
				for ( int line : selectedLines )
				{
					if ( range.contains( line ) )
					{
						pageRange.append( i + 1 ).append( ',' );
						break;
					}
				}
			}
			pageRange.deleteCharAt( pageRange.length() - 1 );
			format.add( new PageRanges( pageRange.toString() ) );

			// also tell the printable exactly which lines are selected so it
			// doesn't have to fetch them itself
			printable.setSelectedLines( selectedLines );
		}


		// ready to print
		final Doc doc = new SimpleDoc( printable, DocFlavor.SERVICE_FORMATTED.PRINTABLE, null );

		// TODO: put this in a swing worker, it can take some time for a large buffer
		Runnable runner = new Runnable()
		{

			public void run()
			{
				try
				{
					job.print( doc, format );
				}
				catch ( PrintException e )
				{
					JOptionPane.showMessageDialog( view, jEdit.getProperty( "print-error.message", new String[] {e.getMessage()} ), jEdit.getProperty( "print-error.title" ), JOptionPane.ERROR_MESSAGE );
				}
			}
		};
		ThreadUtilities.runInBackground( runner );
	}	//}}}


	/**
	 * This is intended for use by classes that need to know the page ranges
	 * of the buffer.
	 */
	public static HashMap<Integer, Range> getPageRanges( View view, Buffer buffer )
	{
		loadPrintSpec();
		BufferPrintable1_7 printable = new BufferPrintable1_7( format, view, buffer );
		return BufferPrinter1_7.getPageRanges( printable, format );
	}


	// have the printable calculate the pages and ranges, the map has the page
	// number as the key, a range containing the start and end line numbers of
	// that page
	private static HashMap<Integer, Range> getPageRanges( BufferPrintable1_7 printable, PrintRequestAttributeSet attributes )
	{
		PageFormat pageFormat = createPageFormat( attributes );
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		BufferedImage image = new BufferedImage(new Double(pageFormat.getImageableWidth()).intValue(), new Double(pageFormat.getImageableHeight()).intValue(), BufferedImage.TYPE_INT_RGB);
		Graphics graphics = ge.createGraphics(image);
		Paper paper = pageFormat.getPaper();
		Rectangle2D.Double clipRegion = new Rectangle2D.Double(paper.getImageableX(), paper.getImageableY(), paper.getImageableWidth(), paper.getImageableHeight());
		graphics.setClip(clipRegion);
		try 
		{
			return printable.calculatePages( graphics, pageFormat );
		}
		catch(Exception e) 
		{
			return null;
		}
	}


	// create a page format using the values from the given attribute set
	private static PageFormat createPageFormat( PrintRequestAttributeSet attributes )
	{
		Paper paper = new Paper();
		MediaPrintableArea mpa = ( MediaPrintableArea )attributes.get( MediaPrintableArea.class );
		int units = MediaPrintableArea.INCH;
		double dpi = 72.0;		// Paper uses 72 dpi
		double x = ( double )mpa.getX( units ) * dpi;
		double y = ( double )mpa.getY( units ) * dpi;
		double w = ( double )mpa.getWidth( units ) * dpi;
		double h = ( double )mpa.getHeight( units ) * dpi;
		paper.setImageableArea( x, y, w, h );

		int orientation = PageFormat.PORTRAIT;
		OrientationRequested or = ( OrientationRequested )attributes.get( OrientationRequested.class );
		if ( OrientationRequested.LANDSCAPE.equals( or ) || OrientationRequested.REVERSE_LANDSCAPE.equals( or ) )
		{
			orientation = PageFormat.LANDSCAPE;
		}


		PageFormat pageFormat = new PageFormat();
		pageFormat.setPaper( paper );
		pageFormat.setOrientation( orientation );

		return pageFormat;
	}


	// {{{ loadPrintSpec() method
	// this finds a previously saved print attribute set in the settings directory,
	// or creates a new, empty attribute set if not found.
	private static void loadPrintSpec()
	{
		format = new HashPrintRequestAttributeSet();

		String settings = jEdit.getSettingsDirectory();
		if ( settings != null )
		{
			String printSpecPath = MiscUtilities.constructPath( settings, "printspec" );
			File filePrintSpec = new File( printSpecPath );

			if ( filePrintSpec.exists() )
			{
				FileInputStream fileIn;
				ObjectInputStream obIn = null;
				try
				{
					fileIn = new FileInputStream( filePrintSpec );
					obIn = new ObjectInputStream( fileIn );
					format = ( HashPrintRequestAttributeSet )obIn.readObject();
				}
				catch ( Exception e )
				{
					Log.log( Log.ERROR, BufferPrinter1_7.class, e );
				}
				finally
				{
					try

					{
						if ( obIn != null )
						{
							obIn.close();
						}
					}
					catch ( IOException e )	// NOPMD
					{
					}	
				}
			}
		}
		MediaPrintableArea mpa = ( MediaPrintableArea )format.get( MediaPrintableArea.class );
		if (mpa == null)
		{
			// assume US Letter size - why? Because I live in the US
			mpa = new MediaPrintableArea(0.5f, 0.5f, 10.0f, 7.5f, MediaPrintableArea.INCH);
			format.add(mpa);
		}
	}


	private static void savePrintSpec()
	{
		String settings = jEdit.getSettingsDirectory();
		if ( settings == null )
		{
			return;
		}


		String printSpecPath = MiscUtilities.constructPath( settings, "printspec" );
		File filePrintSpec = new File( printSpecPath );

		FileOutputStream fileOut;
		ObjectOutputStream objectOut = null;
		try
		{
			fileOut = new FileOutputStream( filePrintSpec );
			objectOut = new ObjectOutputStream( fileOut );
			objectOut.writeObject( format );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			if ( objectOut != null )
			{
				try
				{
					objectOut.flush();
				}
				catch ( IOException e )	// NOPMD
				{
				}	
				try
				{
					objectOut.close();
				}
				catch ( IOException e )	// NOPMD
				{
				}	
			}
		}
	}



	// print job listener, does clean up when the print job is complete and shows
	// the user any errors generated by the printing system
	static class JobListener extends PrintJobAdapter
	{

		private View view;


		public JobListener( View view )
		{
			this.view = view;
		}


		@Override
		public void printJobCompleted( PrintJobEvent pje )
		{

			// if the print service is a "print to file" service, then need to
			// flush and close the output stream.
			PrintService printService = pje.getPrintJob().getPrintService();
			if ( printService instanceof StreamPrintService )
			{
				StreamPrintService streamService = ( StreamPrintService )printService;
				OutputStream outputStream = streamService.getOutputStream();
				try
				{
					outputStream.flush();
				}
				catch ( Exception e )	// NOPMD
				{	
				}
				try
				{
					outputStream.close();
				}
				catch ( Exception e ) 	// NOPMD
				{	
				}
			}

			view.getStatus().setMessageAndClear( "Printing complete." );
		}


		@Override
		public void printJobFailed( PrintJobEvent pje )
		{
			JOptionPane.showMessageDialog( view, jEdit.getProperty( "print-error.message", new String[] {"Print job failed."} ), jEdit.getProperty( "print-error.title" ), JOptionPane.ERROR_MESSAGE );
		}


		@Override
		public void printJobRequiresAttention( PrintJobEvent pje )
		{
			JOptionPane.showMessageDialog( view, jEdit.getProperty( "print-error.message", new String[] {"Check the printer."} ), jEdit.getProperty( "print-error.title" ), JOptionPane.ERROR_MESSAGE );
		}
	}

	private static PrintRequestAttributeSet format;
	private static DocPrintJob job;
}
