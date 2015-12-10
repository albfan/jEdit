/*
 * HelpViewerDialog.java - HTML Help viewer
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1999, 2005 Slava Pestov, Nicholas O'Leary
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

package org.gjt.sp.jedit.help;

//{{{ Imports
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


import java.awt.datatransfer.StringSelection;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import javax.swing.SwingWorker;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import javax.swing.text.html.HTML;

import javax.swing.text.AttributeSet;
import javax.swing.text.Element;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.MiscUtilities;

import org.gjt.sp.jedit.EditBus.EBHandler;
import org.gjt.sp.jedit.io.AutoDetection;
import org.gjt.sp.jedit.io.RegexEncodingDetector;
import org.gjt.sp.jedit.msg.PluginUpdate;

import org.gjt.sp.util.Log;

import static org.gjt.sp.jedit.help.HelpHistoryModel.HistoryEntry;
//}}}

/**
 * jEdit's searchable help viewer. It uses a Swing JEditorPane to display the HTML,
 * and implements a URL history.
 * @author Slava Pestov
 * @version $Id$
 */
public class HelpViewer extends JFrame implements HelpViewerInterface, HelpHistoryModelListener
{
	private static final long serialVersionUID = 1L;
	private static final RegexEncodingDetector ENCODING_DETECTOR = new RegexEncodingDetector(":encoding=([^:]+):", "$1");

	//{{{ HelpViewer constructor
	/**
	 * Creates a new help viewer with the default help page.
	 * @since jEdit 4.0pre4
	 */
	public HelpViewer()
	{
		this("welcome.html");
	} //}}}

	//{{{ HelpViewer constructor
	/**
	 * Creates a new help viewer for the specified URL.
	 * @param url The URL
	 */
	public HelpViewer(URL url)
	{
		this(url.toString());
	} //}}}

	//{{{ HelpViewer constructor
	/**
	 * Creates a new help viewer for the specified URL.
	 * @param url The URL
	 */
	public HelpViewer(String url)
	{
		super(jEdit.getProperty("helpviewer.title"));

		setIconImage(GUIUtilities.getEditorIcon());

		try
		{
			baseURL = new File(MiscUtilities.constructPath(
				jEdit.getJEditHome(),"doc")).toURI().toURL().toString();
		}
		catch(MalformedURLException mu)
		{
			Log.log(Log.ERROR,this,mu);
			// what to do?
		}

		ActionHandler actionListener = new ActionHandler();

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab(jEdit.getProperty("helpviewer.toc.label"),
			toc = new HelpTOCPanel(this));
		tabs.addTab(jEdit.getProperty("helpviewer.search.label"),
			new HelpSearchPanel(this));
		tabs.setMinimumSize(new Dimension(0,0));

		JPanel rightPanel = new JPanel(new BorderLayout());

		Box toolBar = new Box(BoxLayout.X_AXIS);
		//toolBar.setFloatable(false);

		toolBar.add(title = new JLabel());
		toolBar.add(Box.createGlue());
		historyModel = new HelpHistoryModel(25);
		back = new HistoryButton(HistoryButton.BACK,historyModel);
		back.addActionListener(actionListener);
		toolBar.add(back);
		forward = new HistoryButton(HistoryButton.FORWARD,historyModel);
		forward.addActionListener(actionListener);
		toolBar.add(forward);
		back.setPreferredSize(forward.getPreferredSize());
		rightPanel.add(BorderLayout.NORTH,toolBar);

		viewer = new JEditorPane();
		viewer.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,
			Boolean.TRUE);
		
		viewer.setEditable(false);
		viewer.addHyperlinkListener(new LinkHandler());
			 
		viewer.setFont(jEdit.getFontProperty("helpviewer.font"));
		viewer.addPropertyChangeListener(new PropertyChangeHandler());
		viewer.addKeyListener(new KeyHandler());
		viewer.addMouseListener(new MouseHandler());

		viewerScrollPane = new JScrollPane(viewer);

		rightPanel.add(BorderLayout.CENTER,viewerScrollPane);

		splitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
					  tabs,
					  rightPanel);
		splitter.setBorder(null);


		getContentPane().add(BorderLayout.CENTER,splitter);

		historyModel.addHelpHistoryModelListener(this);
		historyUpdated();

		gotoURL(url,true,0);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		getRootPane().setPreferredSize(new Dimension(750,500));

		pack();
		GUIUtilities.loadGeometry(this,"helpviewer");
		GUIUtilities.addSizeSaver(this,"helpviewer");

		EditBus.addToBus(this);

		setVisible(true);

		EventQueue.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				splitter.setDividerLocation(jEdit.getIntegerProperty(
					"helpviewer.splitter",250));
				viewer.requestFocus();
			}
		});
	} //}}}

	//{{{ gotoURL() method
	/**
	 * Displays the specified URL in the HTML component.
	 * 
	 * @param url 		 The URL
	 * @param addToHistory   Should the URL be added to the back/forward
	 * 			 history?
	 * @param scrollPosition The vertical scrollPosition
	 */
	@Override
	public void gotoURL(String url, final boolean addToHistory, final int scrollPosition)
	{
		// the TOC pane looks up user's guide URLs relative to the
		// doc directory...
		String shortURL;
		if (MiscUtilities.isURL(url))
		{
			if (url.startsWith(baseURL))
			{
				shortURL = url.substring(baseURL.length());
				if(shortURL.startsWith("/"))
				{
					shortURL = shortURL.substring(1);
				}
			}
			else
			{
				shortURL = url;
			}
		}
		else
		{
			shortURL = url;
			if(baseURL.endsWith("/"))
			{
				url = baseURL + url;
			}
			else
			{
				url = baseURL + '/' + url;
			}
		}

		// reset default cursor so that the hand cursor doesn't
		// stick around
		viewer.setCursor(Cursor.getDefaultCursor());

		try
		{
			final URL _url = new URL(url);
			final String _shortURL = shortURL;
			if(!_url.equals(viewer.getPage()))
			{
				title.setText(jEdit.getProperty("helpviewer.loading"));
			}
			else
			{
				/* don't show loading msg because we won't
				   receive a propertyChanged */
			}

			historyModel.setCurrentScrollPosition(viewer.getPage(),getCurrentScrollPosition());
			
			/* call setPage asynchronously, because it can block when
			   one can't connect to host.
			   Calling setPage outside from the EDT violates
			   the single-tread rule of Swing, but it's an experienced workaround
			   (see merge request #2984022 - fix blocking HelpViewer
			   https://sourceforge.net/tracker/?func=detail&aid=2984022&group_id=588&atid=1235750
			   for discussion).
			   Once jEdit sets JDK 7 as dependency, all this should be
			   reverted to synchronous code.
			 */
			SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>()
			{
				private boolean success;
				@Override
				protected Void doInBackground() throws Exception
				{
					try
					{
						// reset encoding
						viewer.putClientProperty("charset", null);
						// guess encoding
						if(_url.getPath().matches(".+\\.([tT][xX][tT])"))
						{
							URLConnection connection = _url.openConnection();
							if(connection.getContentEncoding() == null)
							{
								InputStream is = connection.getInputStream();
								BufferedInputStream in = AutoDetection.getMarkedStream(is);
								String encoding = ENCODING_DETECTOR.detectEncoding(in);
								if(encoding != null)
								{
									// JEditorPane uses charset to create the reader passed to the
									// EditorKit in JEditorPane.read().
									viewer.putClientProperty("charset", encoding);
								}
								in.close();
							}
						}
						viewer.setPage(_url);
						success = true;
					}
					catch(IOException io)
					{
						Log.log(Log.ERROR,this,io);
						String[] args = { _url.toString(), io.toString() };
						GUIUtilities.error(HelpViewer.this,"read-error",args);
					}
					return null;
				}

				@Override
				protected void done()
				{
					if (success)
					{
						if (scrollPosition != 0)
						{
							viewerScrollPane.getVerticalScrollBar().setValue(scrollPosition);
						}
						if(addToHistory)
						{
							historyModel.addToHistory(_url.toString());
						}

						HelpViewer.this.shortURL = _shortURL;

						// select the appropriate tree node.
						if(_shortURL != null)
						{
							toc.selectNode(_shortURL);
						}

						viewer.requestFocus();
					}
				}
			};
			worker.execute();
		}
		catch(MalformedURLException mf)
		{
			Log.log(Log.ERROR,this,mf);
			String[] args = { url, mf.getMessage() };
			GUIUtilities.error(this,"badurl",args);
		}
	} //}}}

	//{{{ getCurrentScrollPosition() method
	int getCurrentScrollPosition() {
		return viewerScrollPane.getVerticalScrollBar().getValue();
	} //}}}

	//{{{ getCurrentPage() method
	URL getCurrentPage() {
		return viewer.getPage();
	} //}}}

	//{{{ dispose() method
	@Override
	public void dispose()
	{
		EditBus.removeFromBus(this);
		jEdit.setIntegerProperty("helpviewer.splitter",
			splitter.getDividerLocation());
		super.dispose();
	} //}}}

	//{{{ handlePluginUpdate() method
	@EBHandler
	public void handlePluginUpdate(PluginUpdate pmsg)
	{
		if(pmsg.getWhat() == PluginUpdate.LOADED
				|| pmsg.getWhat() == PluginUpdate.UNLOADED)
			{
				if(!pmsg.isExiting())
				{
					if(!queuedTOCReload)
						queueTOCReload();
					queuedTOCReload = true;
				}
			}
	} //}}}

	//{{{ getBaseURL() method
	@Override
	public String getBaseURL()
	{
		return baseURL;
	} //}}}

	//{{{ getShortURL() method
	@Override
	public String getShortURL()
	{
		return shortURL;
	} //}}}

	//{{{ historyUpdated() method
	@Override
	public void historyUpdated()
	{
		back.setEnabled(historyModel.hasPrevious());
		forward.setEnabled(historyModel.hasNext());
	} //}}}

	//{{{ getComponent method
	@Override
	public Component getComponent()
	{
		return getRootPane();
	} //}}}

	//{{{ Private members

	//{{{ Instance members
	private String baseURL;
	private String shortURL;
	private final HistoryButton back;
	private final HistoryButton forward;
	private final JEditorPane viewer;
	private final JScrollPane viewerScrollPane;
	private final JLabel title;
	private final JSplitPane splitter;
	private final HelpHistoryModel historyModel;
	private final HelpTOCPanel toc;
	private boolean queuedTOCReload;
	//}}}

	//{{{ queueTOCReload() method
	@Override
	public void queueTOCReload()
	{
		EventQueue.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				queuedTOCReload = false;
				toc.load();
			}
		});
	} //}}}

	//}}}

	//{{{ Inner classes

	//{{{ ActionHandler class
	class ActionHandler implements ActionListener
	{
		//{{{ actionPerformed() class
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			Object source = evt.getSource();
			String actionCommand = evt.getActionCommand();
			int separatorPosition = actionCommand.lastIndexOf(':');
			String url;
			int scrollPosition;
			if (-1 == separatorPosition)
			{
				url = actionCommand;
				scrollPosition = 0;
			}
			else
			{
				url = actionCommand.substring(0,separatorPosition);
				scrollPosition = Integer.parseInt(actionCommand.substring(separatorPosition+1));
			}
			if (!url.isEmpty())
			{
				gotoURL(url,false,scrollPosition);
				return;
			}

			if(source == back)
			{
				HistoryEntry entry = historyModel.back(HelpViewer.this);
				if(entry == null)
				{
					javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null); 
				}
				else
				{
					gotoURL(entry.url,false,entry.scrollPosition);
				}
			}
			else if(source == forward)
			{
				HistoryEntry entry = historyModel.forward(HelpViewer.this);
				if(entry == null)
				{
					javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null); 
				}
				else
				{
					gotoURL(entry.url,false,entry.scrollPosition);
				}
			}
		} //}}}
	} //}}}

	//{{{ LinkHandler class
	class LinkHandler implements HyperlinkListener
	{
		//{{{ hyperlinkUpdate() method
		@Override
		public void hyperlinkUpdate(HyperlinkEvent evt)
		{
			if(evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
			{
				if(evt instanceof HTMLFrameHyperlinkEvent)
				{
					((HTMLDocument)viewer.getDocument())
						.processHTMLFrameHyperlinkEvent(
						(HTMLFrameHyperlinkEvent)evt);
					historyUpdated();
				}
				else
				{
					URL url = evt.getURL();
					if(url != null)
					{
						gotoURL(url.toString(),true,0);
					}
				}
			}
			else if (evt.getEventType() == HyperlinkEvent.EventType.ENTERED)
			{
				viewer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			else if (evt.getEventType() == HyperlinkEvent.EventType.EXITED)
			{
				viewer.setCursor(Cursor.getDefaultCursor());
			}
		} //}}}
	} //}}}

	//{{{ PropertyChangeHandler class
	class PropertyChangeHandler implements PropertyChangeListener
	{
		@Override
		public void propertyChange(PropertyChangeEvent evt)
		{
			if("page".equals(evt.getPropertyName()))
			{
				String titleStr = (String)viewer.getDocument()
					.getProperty("title");
				if(titleStr == null)
				{
					titleStr = MiscUtilities.getFileName(
						viewer.getPage().toString());
				}
				title.setText(titleStr);
				historyModel.updateTitle(viewer.getPage().toString(),
					titleStr);
			}
		}
	} //}}}

	//{{{ KeyHandler class
	private class KeyHandler extends KeyAdapter
	{
		@Override
		public void keyPressed(KeyEvent ke)
		{
			switch (ke.getKeyCode())
			{
			case KeyEvent.VK_UP:
				JScrollBar scrollBar = viewerScrollPane.getVerticalScrollBar();
				scrollBar.setValue(scrollBar.getValue()-scrollBar.getUnitIncrement(-1));
				ke.consume();
				break;
			case KeyEvent.VK_DOWN:
				scrollBar = viewerScrollPane.getVerticalScrollBar();
				scrollBar.setValue(scrollBar.getValue()+scrollBar.getUnitIncrement(1));
				ke.consume();
				break;
			case KeyEvent.VK_LEFT:
				scrollBar = viewerScrollPane.getHorizontalScrollBar();
				scrollBar.setValue(scrollBar.getValue()-scrollBar.getUnitIncrement(-1));
				ke.consume();
				break;
			case KeyEvent.VK_RIGHT:
				scrollBar = viewerScrollPane.getHorizontalScrollBar();
				scrollBar.setValue(scrollBar.getValue()+scrollBar.getUnitIncrement(1));
				ke.consume();
				break;
			case KeyEvent.VK_HOME:
				scrollBar = viewerScrollPane.getHorizontalScrollBar();
				scrollBar.setValue(0);
				scrollBar = viewerScrollPane.getVerticalScrollBar();
				scrollBar.setValue(0);
				ke.consume();
				break;
			case KeyEvent.VK_END:
				scrollBar = viewerScrollPane.getHorizontalScrollBar();
				scrollBar.setValue(scrollBar.getMaximum());
				scrollBar = viewerScrollPane.getVerticalScrollBar();
				scrollBar.setValue(scrollBar.getMaximum());
				ke.consume();
				break;
			}
		}
	} //}}}

	//{{{ MouseHandler class
	private class MouseHandler extends MouseAdapter
	{
		@Override
		public void mousePressed(MouseEvent me)
		{
			if(me.isPopupTrigger())
			{
				handlePopupTrigger(me);
			}
		}

		@Override
		public void mouseReleased(MouseEvent me)
		{
			if(me.isPopupTrigger())
			{
				handlePopupTrigger(me);
			}
		}

		private void handlePopupTrigger(MouseEvent me)
		{
            int caret = viewer.getUI().viewToModel(viewer, me.getPoint());
            if (caret >= 0 && viewer.getDocument() instanceof HTMLDocument)
            {
                HTMLDocument hdoc = (HTMLDocument) viewer.getDocument();
                Element elem = hdoc.getCharacterElement(caret);
                if (elem.getAttributes().getAttribute(HTML.Tag.A) != null)
                {
                    Object attribute = elem.getAttributes().getAttribute(HTML.Tag.A);
                    if (attribute instanceof AttributeSet)
                    {
                        AttributeSet set = (AttributeSet) attribute;
                        final String href = (String) set.getAttribute(HTML.Attribute.HREF);
                        if (href != null)
                        {
							JPopupMenu popup = new JPopupMenu();
							JMenuItem copy = popup.add(jEdit.getProperty("helpviewer.copy-link.label"));
							copy.addActionListener(new ActionListener(){
								public void actionPerformed(ActionEvent e)
								{
									StringSelection url = new StringSelection(href);
									Toolkit.getDefaultToolkit().getSystemClipboard().setContents(url, url);
								}
							});
							popup.show(viewer, me.getX(), me.getY());
                        }
                    }
                }
            }
		}
	} //}}}
	//}}}
}
