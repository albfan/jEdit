/*
 * EditPlugin.java - Abstract class all plugins must implement
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

package org.gjt.sp.jedit;

import javax.swing.JMenuItem;
import java.util.*;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.gui.OptionsDialog;
import org.gjt.sp.jedit.menu.EnhancedMenu;

/**
 * The abstract base class that every plugin must implement.
 * Alternatively, instead of extending this class, a plugin core class can
 * extend {@link EBPlugin} to automatically receive EditBus messages.
 *
 * <h3>Basic plugin information properties</h3>
 *
 * Note that in all cases above where a class name is needed, the fully
 * qualified class name, including the package name, if any, must be used.<p>
 *
 * The following properties are required for jEdit to load the plugin:
 *
 * <ul>
 * <li><code>plugin.<i>class name</i>.activate</code> - set this to
 * <code>defer</code> if your plugin only needs to be loaded when it is first
 * invoked; set it to <code>startup</code> if your plugin must be loaded at
 * startup regardless; set it to a whitespace-separated list of property names
 * if your plugin should be loaded if at least one of these properties is set.
 * Note that if this property is <b>not</b> set, the plugin is loaded like an
 * old-style jEdit 4.1 plugin.
 * </li>
 * <li><code>plugin.<i>class name</i>.name</code></li>
 * <li><code>plugin.<i>class name</i>.version</code></li>
 * <li><code>plugin.<i>class name</i>.jars</code> - only needed if your plugin
 * bundles external JAR files. Contains a whitespace-separated list of JAR
 * file names. Without this property, the plugin manager will leave behind the
 * external JAR files when removing the plugin.</li>
 * </ul>
 *
 * The following properties are optional but recommended:
 *
 * <ul>
 * <li><code>plugin.<i>class name</i>.author</code></li>
 * <li><code>plugin.<i>class name</i>.docs</code> - the path to plugin
 * documentation in HTML format within the JAR file.</li>
 * </ul>
 *
 * <h3>Plugin dependency properties</h3>
 *
 * Plugin dependencies are also specified using properties.
 * Each dependency is defined in a property named with
 * <code>plugin.<i>class name</i>.depend.</code> followed by a number.
 * Dependencies must be numbered in order, starting from zero.<p>
 *
 * The value of a dependency property has one of the following forms:
 *
 * <ul>
 * <li><code>jdk <i>minimum Java version</i></code></li>
 * <li><code>jedit <i>minimum jEdit version</i></code> - note that this must be
 * a version number in the form returned by {@link jEdit#getBuild()},
 * not {@link jEdit#getVersion()}. Note that the documentation here describes
 * the jEdit 4.2 plugin API, so this dependency must be set to at least
 * <code>04.02.01.00</code>.</li>
 * <li><code>plugin <i>plugin</i> <i>version</i></code> - the fully quailified
 * plugin class name must be specified.</li>
 * </ul>
 *
 * <h3>Plugin menu item properties</h3>
 *
 * To add your plugin to the view's <b>Plugins</b> menu, define one of these two
 * properties:
 *
 * <ul>
 * <li><code>plugin.<i>class name</i>.menu-item</code> - if this is defined,
 * the action named by this property is added to the <b>Plugins</b> menu.</li>
 * <li><code>plugin.<i>class name</i>.menu</code> - if this is defined,
 * a sub-menu is added to the <b>Plugins</b> menu whose content is the
 * whitespace-separated list of action names in this property. A separator may
 * be added to the sub-menu by listing <code>-</code> in the property.</li>
 * </ul>
 *
 * If you want the plugin's menu items to be determined at runtime, define a
 * property <code>plugin.<i>class name</i>.menu.code</code> to be BeanShell
 * code that evaluates to an implementation of
 * {@link org.gjt.sp.jedit.menu.DynamicMenuProvider}.<p>
 *
 * To add your plugin to the file system browser's <b>Plugins</b> menu, define
 * one of these two properties:
 *
 * <ul>
 * <li><code>plugin.<i>class name</i>.browser-menu-item</code> - if this is
 * defined, the action named by this property is added to the <b>Plugins</b>
 * menu.</li>
 * <li><code>plugin.<i>class name</i>.browser-menu</code> - if this is defined,
 * a sub-menu is added to the <b>Plugins</b> menu whose content is the
 * whitespace-separated list of action names in this property. A separator may
 * be added to the sub-menu by listing <code>-</code> in the property.</li>
 * </ul>
 *
 * In all cases, each action's
 * menu item label is taken from the <code><i>action name</i>.label</code>
 * property. View actions are defined in an <code>actions.xml</code>
 * file, file system browser actions are defined in a
 * <code>browser.actions.xml</code> file; see {@link ActionSet}.
 *
 * <h3>Plugin option pane properties</h3>
 *
 * To add your plugin to the <b>Plugin Options</b> dialog box, define one of
 * these two properties:
 *
 * <ul>
 * <li><code>plugin.<i>class name</i>.option-pane</code> - if this is defined,
 * the option pane named by this property is added to the <b>Plugin Options</b>
 * menu.</li>
 * <li><code>plugin.<i>class name</i>.option-group</code> - if this is defined,
 * a branch node is added to the <b>Plugin Options</b> dialog box whose content
 * is the whitespace-separated list of option pane names in this property.</li>
 * </ul>
 *
 * Then for each option pane name, define these two properties:
 *
 * <ul>
 * <li><code>options.<i>option pane name</i>.label</code> - the label to show
 * for the pane in the dialog box.</li>
 * <li><code>options.<i>option pane name</i>.code</code> - BeanShell code that
 * evaluates to an instance of the {@link OptionPane} class.</li>
 *
 * <h3>Example</h3>
 *
 * Here is an example set of plugin properties:
 *
 * <pre>plugin.QuickNotepadPlugin.activate=defer
 *plugin.QuickNotepadPlugin.name=QuickNotepad
 *plugin.QuickNotepadPlugin.author=John Gellene
 *plugin.QuickNotepadPlugin.version=4.2
 *plugin.QuickNotepadPlugin.docs=QuickNotepad.html
 *plugin.QuickNotepadPlugin.depend.0=jedit 04.02.01.00
 *plugin.QuickNotepadPlugin.menu=quicknotepad \
 *    - \
 *    quicknotepad.choose-file \
 *    quicknotepad.save-file \
 *    quicknotepad.copy-to-buffer
 *plugin.QuickNotepadPlugin.option-pane=quicknotepad</pre>
 *
 * Note that action and option pane labels are not shown in the above example.
 *
 * @see org.gjt.sp.jedit.jEdit#getProperty(String)
 * @see org.gjt.sp.jedit.jEdit#getPlugin(String)
 * @see org.gjt.sp.jedit.jEdit#getPlugins()
 * @see org.gjt.sp.jedit.jEdit#getPluginJAR(String)
 * @see org.gjt.sp.jedit.jEdit#getPluginJARs()
 * @see org.gjt.sp.jedit.jEdit#addPluginJAR(String)
 * @see org.gjt.sp.jedit.jEdit#removePluginJAR(PluginJAR,boolean)
 * @see org.gjt.sp.jedit.ActionSet
 * @see org.gjt.sp.jedit.gui.DockableWindowManager
 * @see org.gjt.sp.jedit.OptionPane
 * @see org.gjt.sp.jedit.PluginJAR
 * @see org.gjt.sp.jedit.ServiceManager
 *
 * @author Slava Pestov
 * @author John Gellene (API documentation)
 * @version $Id$
 * @since jEdit 2.1pre1
 */
public abstract class EditPlugin
{
	//{{{ start() method
	/**
	 * jEdit calls this method when the plugin is being activated, either
	 * during startup or at any other time. A plugin can get activated for
	 * a number of reasons:
	 *
	 * <ul>
	 * <li>The plugin is written for jEdit 4.1 or older, in which case it
	 * will always be loaded at startup.</li>
	 * <li>The plugin has its <code>activate</code> property set to
	 * <code>startup</code>, in which case it will always be loaded at
	 * startup.</li>
	 * <li>One of the properties listed in the plugin's
	 * <code>activate</code> property is set to <code>true</code>,
	 * in which case it will always be loaded at startup.</li>
	 * <li>One of the plugin's classes is being accessed by another plugin,
	 * a macro, or a BeanShell snippet in a plugin API XML file.</li>
	 * </ul>
	 *
	 * Note that this method is always called from the event dispatch
	 * thread, even if the activation resulted from a class being loaded
	 * from another thread. A side effect of this is that some of your
	 * plugin's code might get executed before this method finishes
	 * running.<p>
	 *
	 * When this method is being called for plugins written for jEdit 4.1
	 * and below, no views or buffers are open. However, this is not the
	 * case for plugins using the new API. For example, if your plugin adds
	 * tool bars to views, make sure you correctly handle the case where
	 * views are already open when the plugin is loaded.<p>
	 *
	 * If your plugin must be loaded on startup, take care to have this
	 * method return as quickly as possible.<p>
	 *
	 * The default implementation of this method does nothing.
	 *
	 * @since jEdit 2.1pre1
	 */
	public void start() {}
	//}}}

	//{{{ stop() method
	/**
	 * jEdit calls this method when the plugin is being unloaded. This can
	 * be when the program is exiting, or at any other time.<p>
	 *
	 * If a plugin uses state information or other persistent data
	 * that should be stored in a special format, this would be a good place
	 * to write the data to storage.  If the plugin uses jEdit's properties
	 * API to hold settings, no special processing is needed for them on
	 * exit, since they will be saved automatically.<p>
	 *
	 * With plugins written for jEdit 4.1 and below, this method is only
	 * called when the program is exiting. However, this is not the case
	 * for plugins using the new API. For example, if your plugin adds
	 * tool bars to views, make sure you correctly handle the case where
	 * views are still open when the plugin is unloaded.<p>
	 *
	 * To avoid memory leaks, this method should ensure that no references
	 * to any objects created by this plugin remain in the heap. In the
	 * case of actions, dockable windows and services, jEdit ensures this
	 * automatically. For other objects, your plugin must clean up maually.
	 * <p>
	 *
	 * The default implementation of this method does nothing.
	 *
	 * @since jEdit 2.1pre1
	 */
	public void stop() {} //}}}

	//{{{ getClassName() method
	/**
	 * Returns the plugin's class name. This might not be the same as
	 * the class of the actual <code>EditPlugin</code> instance, for
	 * example if the plugin is not loaded yet.
	 *
	 * @since jEdit 2.5pre3
	 */
	public String getClassName()
	{
		return getClass().getName();
	} //}}}

	//{{{ getPluginJAR() method
	/**
	 * Returns the JAR file containing this plugin.
	 * @since jEdit 4.2pre1
	 */
	public PluginJAR getPluginJAR()
	{
		return jar;
	} //}}}

	//{{{ createMenuItems() method
	/**
	 * Called by the view when constructing its <b>Plugins</b> menu.
	 * See the description of this class for details about how the
	 * menu items are constructed from plugin properties.
	 *
	 * @since jEdit 4.2pre1
	 */
	public final JMenuItem createMenuItems()
	{
		if(this instanceof Broken)
			return null;

		String menuItemName = jEdit.getProperty("plugin." +
			getClassName() + ".menu-item");
		if(menuItemName != null)
			return GUIUtilities.loadMenuItem(menuItemName);

		String menuProperty = "plugin." + getClassName() + ".menu";
		String codeProperty = "plugin." + getClassName() + ".menu.code";
		if(jEdit.getProperty(menuProperty) != null
			|| jEdit.getProperty(codeProperty) != null)
		{
			String pluginName = jEdit.getProperty("plugin." +
				getClassName() + ".name");
			return new EnhancedMenu(menuProperty,pluginName);
		}

		return null;
	} //}}}

	//{{{ createBrowserMenuItems() method
	/**
	 * Called by the filesystem browser when constructing its
	 * <b>Plugins</b> menu.
	 * See the description of this class for details about how the
	 * menu items are constructed from plugin properties.
	 *
	 * @since jEdit 4.2pre1
	 */
	public final JMenuItem createBrowserMenuItems()
	{
		if(this instanceof Broken)
			return null;

		String menuItemName = jEdit.getProperty("plugin." +
			getClassName() + ".browser-menu-item");
		if(menuItemName != null)
		{
			return GUIUtilities.loadMenuItem(
				VFSBrowser.getActionContext(),
				menuItemName,
				false);
		}

		String menuProperty = "plugin." + getClassName() + ".browser-menu";
		if(jEdit.getProperty(menuProperty) != null)
		{
			String pluginName = jEdit.getProperty("plugin." +
				getClassName() + ".name");
			return new EnhancedMenu(menuProperty,pluginName,
				VFSBrowser.getActionContext());
		}

		return null;
	} //}}}

	//{{{ Deprecated methods

	//{{{ createMenuItems() method
	/**
	 * @deprecated Instead of overriding this method, define properties
	 * as specified in the description of this class.
	 */
	public void createMenuItems(Vector menuItems) {} //}}}

	//{{{ createOptionPanes() method
	/**
	 * @deprecated Instead of overriding this method, define properties
	 * as specified in the description of this class.
	 */
	public void createOptionPanes(OptionsDialog optionsDialog) {} //}}}

	//}}}

	//{{{ Package-private members
	PluginJAR jar;
	//}}}

	//{{{ Broken class
	/**
	 * A placeholder for a plugin that didn't load.
	 * @see jEdit#getPlugin(String)
	 * @see PluginJAR#getPlugin()
	 * @see PluginJAR#activatePlugin()
	 */
	public static class Broken extends EditPlugin
	{
		public String getClassName()
		{
			return clazz;
		}

		// package-private members
		Broken(PluginJAR jar, String clazz)
		{
			this.jar = jar;
			this.clazz = clazz;
		}

		// private members
		private String clazz;
	} //}}}

	//{{{ Deferred class
	/**
	 * A placeholder for a plugin that hasn't been loaded yet.
	 * @see jEdit#getPlugin(String)
	 * @see PluginJAR#getPlugin()
	 * @see PluginJAR#activatePlugin()
	 */
	public static class Deferred extends EditPlugin
	{
		public String getClassName()
		{
			return clazz;
		}

		// package-private members
		Deferred(PluginJAR jar, String clazz)
		{
			this.jar = jar;
			this.clazz = clazz;
		}

		EditPlugin loadPluginClass()
		{
			return null;
		}

		public String toString()
		{
			return "Deferred[" + clazz + "]";
		}

		// private members
		private String clazz;
	} //}}}
}
