/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2010 Matthieu Casanova
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.gjt.sp.util;

/**
 * @since jEdit 4.4pre1
 * @author Matthieu Casanova
 */
public abstract class Task implements Runnable, ProgressObserver
{
	private long value;
	private String status;
	private long maximum;

	private String label;

	private Thread thread;

	public enum State
	{
		Waiting, Running, Done
	}

	private State state;

	protected Task()
	{
		state = State.Waiting;
	}

	public final void run()
	{
		state = State.Running;
		TaskManager.instance.fireRunning(this);
		try
		{
			thread = Thread.currentThread();
			_run();
			thread = null;
		}
		catch (Throwable t)
		{
			Log.log(Log.ERROR, this, t);
		}
		state = State.Done;
		TaskManager.instance.fireDone(this);
	}

	public abstract void _run();

	public final void setValue(long value)
	{
		this.value = value;
		TaskManager.instance.fireValueUpdated(this);
	}

	public final void setMaximum(long maximum)
	{
		this.maximum = maximum;
		TaskManager.instance.fireMaximumUpdated(this);
	}

	public void setStatus(String status)
	{
		this.status = status;
		TaskManager.instance.fireStatusUpdated(this);
	}

	public long getValue()
	{
		return value;
	}

	public String getStatus()
	{
		return status;
	}

	public long getMaximum()
	{
		return maximum;
	}

	public State getState()
	{
		return state;
	}

	public String getLabel()
	{
		return label;
	}

	public void setLabel(String label)
	{
		this.label = label;
	}

	/**
	 * Cancel the task
	 */
	public void cancel()
	{
		if (thread != null)
			thread.interrupt();
	}

	@Override
	public String toString()
	{
		return "Task[" + state + ',' + status + ',' + value + '/' + maximum + ']';
	}
}
