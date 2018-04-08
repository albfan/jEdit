/*
 * AbstractOptionPane.java - Abstract option pane
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1998, 1999, 2000, 2001, 2002 Slava Pestov
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
import javax.swing.*;
import java.awt.*;

//}}}

/**
 * The default implementation of the option pane interface.<p>
 *
 * See {@link EditPlugin} for information on how jEdit obtains and constructs
 * option pane instances.<p>
 *
 * Most option panes extend this implementation of {@link OptionPane}, instead
 * of implementing {@link OptionPane} directly. This class provides a convenient
 * default framework for laying out configuration options.<p>
 *
 * It is derived from Java's <code>JPanel</code> class and uses a
 * <code>GridBagLayout</code> object for component management. Since
 * <code>GridBagLayout</code> can be a bit cumbersome to use, this class
 * contains shortcut methods to simplify layout:
 *
 * <ul>
 * <li>{@link #addComponent(Component)}</li>
 * <li>{@link #addComponent(String,Component)}</li>
 * <li>{@link #addComponent(String,Component,int)}</li>
 * <li>{@link #addComponent(Component,Component)}</li>
 * <li>{@link #addComponent(Component,Component,int)}</li>
 * <li>{@link #addSeparator()}</li>
 * <li>{@link #addSeparator(String)}</li>
 * </ul>
 *
 * @author Slava Pestov
 * @author John Gellene (API documentation)
 * @version $Id$
 */
// even though this class is called AbstractOptionPane, it is not really
// abstract, since BufferOptions uses an instance of it to lay out its
// components.
public class AbstractOptionPane extends JPanel implements OptionPane
{
	//{{{ AbstractOptionPane constructor
	/**
	 * Creates a new option pane.
	 * @param internalName The internal name.
	 * This should be the same name as that provided
	 * in the plugin's <code>plugin.<i>className</i>.option-group</code>
	 * or <code>.option-pane</code> property.
	 *
	 * The option pane's label is set to the
	 * value of the property named <code>options.<i>name</i>.label</code>.
	 */
	public AbstractOptionPane(String internalName)
	{
		name = internalName;
		setLayout(gridBag = new GridBagLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
	} //}}}

	//{{{ getName() method
	/**
	 * Returns the internal name of this option pane. The option pane's label
	 * is set to the value of the property named
	 * <code>options.<i>name</i>.label</code>.
	 */
	@Override
	public String getName()
	{
		return name;
	} //}}}

	//{{{ getComponent() method
	/**
	 * Returns the component that should be displayed for this option pane.
	 * Because this class extends Component, it simply returns "this".
	 */
	@Override
	public Component getComponent()
	{
		return this;
	} //}}}

	//{{{ init() method
	@Override
	public final void init()
	{
		if(!initialized)
		{
			initialized = true;
			_init();
		}
	} //}}}

	//{{{ save() method
	@Override
	public final void save()
	{
		if(initialized)
			_save();
	} //}}}

	//{{{ newLabel()
	/**
	 * @param label The label to associate with comp
	 * @param comp The component to associate the label
	 * @return a label which has the same tooltiptext as the Component
	 *    that it is a label for. This is used to create labels from inside
	 *    AbstractOptionPane.
	 * @since jEdit 4.3pre4
	 */
	public JLabel newLabel(String label, Component comp)
	{
		JLabel retval = new JLabel(label);
		try /* to get the tooltip of the component */
		{
			JComponent jc = (JComponent) comp;
			String tttext = jc.getToolTipText();
			retval.setToolTipText(tttext);
		}
		catch (Exception e)
		{
			/* There probably wasn't a tooltip,
			 * or it wasn't a JComponent.
			   We don't care. */
		}
		return retval;
	}// }}}

	//{{{ addComponent() method
	/**
	 * Adds a labeled component to the option pane. Components are
	 * added in a vertical fashion, one per row. The label is
	 * displayed to the left of the component.
	 * @param label The label
	 * @param comp The component
	 */
	public void addComponent(String label, Component comp)
	{
		JLabel l = newLabel(label, comp);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
		addComponent(l, comp, GridBagConstraints.BOTH);
	}

	/**
	 * Adds a labeled component to the option pane. Components are
	 * added in a vertical fashion, one per row. The label is
	 * displayed to the left of the component.
	 * @param label The label
	 * @param comp The component
	 * @param fill Fill parameter to GridBagConstraints for the right
	 * component
	 */
	public void addComponent(String label, Component comp, int fill)
	{
		JLabel l = newLabel(label, comp);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
		addComponent(l, comp, fill);
	}

	/**
	 * Adds a labeled component to the option pane. Components are
	 * added in a vertical fashion, one per row. The label is
	 * displayed to the left of the component.
	 * @param comp1 The label
	 * @param comp2 The component
	 *
	 * @since jEdit 4.1pre3
	 */
	public void addComponent(Component comp1, Component comp2)
	{
		addComponent(comp1, comp2, GridBagConstraints.BOTH);
	}

	/**
	 * Adds a labeled component to the option pane. Components are
	 * added in a vertical fashion, one per row. The label is
	 * displayed to the left of the component.
	 * @param comp1 The label
	 * @param comp2 The component
	 * @param fill Fill parameter to GridBagConstraints for the right
	 * component
	 *
	 * @since jEdit 4.1pre3
	 */
	public void addComponent(Component comp1, Component comp2, int fill)
	{
		copyToolTips(comp1, comp2);
		GridBagConstraints cons = new GridBagConstraints();
		cons.gridy = y++;
		cons.gridheight = 1;
		cons.gridwidth = 1;
		cons.weightx = 0.0f;
		cons.insets = new Insets(6, 0, 0, 6);
		cons.fill = GridBagConstraints.BOTH;

		gridBag.setConstraints(comp1, cons);
		add(comp1);

		cons.fill = fill;
		cons.gridx = 1;
		cons.weightx = 1.0f;
		gridBag.setConstraints(comp2, cons);
		add(comp2);
	}

	/**
	 * Adds a component to the option pane. Components are
	 * added in a vertical fashion, one per row.
	 * @param comp The component
	 */
	public void addComponent(Component comp)
	{
		GridBagConstraints cons = new GridBagConstraints();
		cons.gridy = y++;
		cons.gridheight = 1;
		cons.gridwidth = GridBagConstraints.REMAINDER;
		cons.fill = GridBagConstraints.NONE;
		cons.anchor = GridBagConstraints.WEST;
		cons.weightx = 1.0f;
		cons.insets = new Insets(6, 0, 0, 0);

		gridBag.setConstraints(comp, cons);
		add(comp);
	}

	/**
	 * Adds a component to the option pane. Components are
	 * added in a vertical fashion, one per row.
	 * @param comp The component
	 * @param fill Fill parameter to GridBagConstraints
	 * @since jEdit 4.2pre2
	 */
	public void addComponent(Component comp, int fill)
	{
		GridBagConstraints cons = new GridBagConstraints();
		cons.gridy = y++;
		cons.gridheight = 1;
		cons.gridwidth = GridBagConstraints.REMAINDER;
		cons.fill = fill;
		cons.anchor = GridBagConstraints.WEST;
		cons.weightx = 1.0f;
		cons.insets = new Insets(6, 0, 0, 0);

		gridBag.setConstraints(comp, cons);
		add(comp);
	} //}}}

	//{{{ copyToolTips() method
	private static void copyToolTips(Component c1, Component c2)
	{
		if (!(c1 instanceof JComponent) || !(c2 instanceof JComponent))
			return;

		JComponent jc1 = (JComponent) c1;
		String tooltip = jc1.getToolTipText();
		int tooltips = 0;
		if (tooltip != null && !tooltip.isEmpty())
			tooltips++;

		JComponent jc2 = (JComponent) c2;
		String tooltip2 = jc2.getToolTipText();
		if (tooltip2 != null && !tooltip2.isEmpty())
		{
			tooltip = tooltip2;
			tooltips++;
		}

		// if there is 0 tooltip, do nothing
		// if there is 1 tooltip, copy it to the two components
		// if there is 2 tooltip, each component has it's tooltip, do
		//     nothing
		if (tooltips == 1)
		{
			jc1.setToolTipText(tooltip);
			jc2.setToolTipText(tooltip);
		}
	} //}}}

	//{{{ addSeparator() methods
	/**
	 * Adds a separator component.
	 * @since jEdit 4.1pre7
	 */
	public void addSeparator()
	{
		addComponent(Box.createVerticalStrut(6));

		JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);

		GridBagConstraints cons = new GridBagConstraints();
		cons.gridy = y++;
		cons.gridheight = 1;
		cons.gridwidth = GridBagConstraints.REMAINDER;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.WEST;
		cons.weightx = 1.0f;

		gridBag.setConstraints(sep, cons);
		add(sep);

		addComponent(Box.createVerticalStrut(6));
	}

	/**
	 * Adds a separator component.
	 * @param label The separator label property
	 * @since jEdit 2.6pre2
	 */
	public void addSeparator(String label)
	{
		if(y != 0)
			addComponent(Box.createVerticalStrut(6));

		Box box = new Box(BoxLayout.X_AXIS);
		Box box2 = new Box(BoxLayout.Y_AXIS);
		box2.add(Box.createGlue());
		box2.add(new JSeparator(SwingConstants.HORIZONTAL));
		box2.add(Box.createGlue());
		box.add(box2);
		JLabel l = new JLabel(jEdit.getProperty(label));
		l.setMaximumSize(l.getPreferredSize());
		box.add(l);
		Box box3 = new Box(BoxLayout.Y_AXIS);
		box3.add(Box.createGlue());
		box3.add(new JSeparator(SwingConstants.HORIZONTAL));
		box3.add(Box.createGlue());
		box.add(box3);

		GridBagConstraints cons = new GridBagConstraints();
		cons.gridy = y++;
		cons.gridheight = 1;
		cons.gridwidth = GridBagConstraints.REMAINDER;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.WEST;
		cons.weightx = 1.0f;
		cons.insets = new Insets(6, 0, 0, 0);

		gridBag.setConstraints(box,cons);
		add(box);
	} //}}}

	//{{{ Protected members
	/**
	 * Has the option pane been initialized?
	 */
	protected boolean initialized;

	/**
	 * The layout manager.
	 */
	protected GridBagLayout gridBag;

	/**
	 * The number of components already added to the layout manager.
	 */
	protected int y;

	/**
	 * This method should create and arrange the components of the option pane
	 * and initialize the option data displayed to the user. This method
	 * is called when the option pane is first displayed, and is not
	 * called again for the lifetime of the object.
	 */
	protected void _init() {}

	/**
	 * Called when the options dialog's "ok" button is clicked.
	 * This should save any properties being edited in this option
	 * pane.
	 */
	protected void _save() {}

	//}}}

	//{{{ Private members
	private final String name;
	//}}}
}
