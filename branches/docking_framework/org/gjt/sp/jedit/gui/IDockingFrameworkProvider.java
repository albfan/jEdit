package org.gjt.sp.jedit.gui;

import javax.swing.JPanel;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.View.ViewConfig;
import org.gjt.sp.jedit.gui.DockableWindowManager.DockingLayout;

public interface IDockingFrameworkProvider {
	DockableWindowManager create(View view, DockableWindowFactory instance,
			ViewConfig config);
	DockingLayout createDockingLayout();
}
