/*
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2006 Matthieu Casanova
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

import org.gjt.sp.jedit.msg.RegisterChanged;

/**
 * The concrete RegistersListener of jEdit.
 * @author Matthieu Casanova
 * @version $Id: FoldHandler.java 5568 2006-07-10 20:52:23Z kpouer $
 */
class JEditRegistersListener implements RegistersListener
{
	public void registerChanged(char name)
	{
		EditBus.send(new RegisterChanged(null, name));
	}
}