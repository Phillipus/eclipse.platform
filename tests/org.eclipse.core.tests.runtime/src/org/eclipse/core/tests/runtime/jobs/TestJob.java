/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.tests.runtime.jobs;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;

/**
 * 
 */
class TestJob extends Job {
	private int ticks;
	private int tickLength;
	private int runCount = 0;
	public TestJob(String name) {
		this(name, 10, 100);
	}
	public TestJob(String name, int ticks, int tickLength) {
		super(name);
		this.ticks = ticks;
		this.tickLength = tickLength;
	}
	/**
	 * Returns the number of times this job instance has been run.
	 */
	public synchronized int getRunCount() {
		return runCount;
	}
	public IStatus run(IProgressMonitor monitor) {
		setRunCount(getRunCount()+1);
		//must have positive work
		monitor.beginTask(getName(), ticks <= 0 ? 1 : ticks);
		try {
			for (int i = 0; i < ticks; i++) {
				monitor.subTask("Tick: " + i);
				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
				try {
					Thread.sleep(tickLength);
				} catch (InterruptedException e) {
				}
				monitor.worked(1);
			}
			if (ticks <= 0)
				monitor.worked(1);
		} finally {
			monitor.done();
		}
		return Status.OK_STATUS;
	}
	private synchronized void setRunCount(int count) {
		runCount = count;
	}
}