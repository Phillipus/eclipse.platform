/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.internal.ui.dialogs.DetailsDialog;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * Prompts the user for a multi-line comment for releasing to CVS.
 */
public class ReleaseCommentDialog extends DetailsDialog {
	
	CommitCommentArea commitCommentArea;
	//	dialogs settings that are persistent between workbench sessions
	private IDialogSettings settings;
	private static final String HEIGHT_KEY = "width-key"; //$NON-NLS-1$
	private static final String WIDTH_KEY = "height-key"; //$NON-NLS-1$
	
	/**
	 * ReleaseCommentDialog constructor.
	 * 
	 * @param parentShell  the parent of this dialog
	 * @param proposedComment
	 */
	public ReleaseCommentDialog(Shell parentShell, IResource[] resourcesToCommit, String proposedComment, int depth) {
		super(parentShell, Policy.bind("ReleaseCommentDialog.title")); //$NON-NLS-1$
		int shellStyle = getShellStyle();
		setShellStyle(shellStyle | SWT.RESIZE | SWT.MAX);
		commitCommentArea = new CommitCommentArea(this, null);
		// Get a project from which the commit template can be obtained
		if (resourcesToCommit.length > 0) 
		commitCommentArea.setProject(resourcesToCommit[0].getProject());
		commitCommentArea.setProposedComment(proposedComment);
		
		IDialogSettings workbenchSettings = CVSUIPlugin.getPlugin().getDialogSettings();
		this.settings = workbenchSettings.getSection("ReleaseCommentDialog");//$NON-NLS-1$
		if (settings == null) {
			this.settings = workbenchSettings.addNewSection("ReleaseCommentDialog");//$NON-NLS-1$
		}		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.dialogs.DetailsDialog#includeDetailsButton()
	 */
	protected boolean includeDetailsButton() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.dialogs.DetailsDialog#includeErrorMessage()
	 */
	protected boolean includeErrorMessage() {
		return false;
	}
	
	/*
	 * @see Dialog#createDialogArea(Composite)
	 */
	protected void createMainDialogArea(Composite parent) {
		getShell().setText(Policy.bind("ReleaseCommentDialog.title")); //$NON-NLS-1$
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		commitCommentArea.createArea(composite);
		commitCommentArea.addPropertyChangeListener(new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty() == CommitCommentArea.OK_REQUESTED)
					okPressed();
			}
		});
		
		// set F1 help
		WorkbenchHelp.setHelp(composite, IHelpContextIds.RELEASE_COMMENT_DIALOG);	
        Dialog.applyDialogFont(parent);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#getInitialSize()
	 */
	protected Point getInitialSize() {
		int width, height;
		try {
			height = settings.getInt(HEIGHT_KEY);
			width = settings.getInt(WIDTH_KEY);
		} catch(NumberFormatException e) {
			return super.getInitialSize();
		}
		Point p = super.getInitialSize();
		return new Point(width, p.y);
	}
	
	public String getComment() {
		return commitCommentArea.getComment();
	}
	
	public IResource[] getResourcesToCommit() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.dialogs.DetailsDialog#createDropDownDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	protected Composite createDropDownDialogArea(Composite parent) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.dialogs.DetailsDialog#updateEnablements()
	 */
	protected void updateEnablements() {	
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#close()
	 */
	public boolean close() {
		Rectangle bounds = getShell().getBounds();
		settings.put(HEIGHT_KEY, bounds.height);
		settings.put(WIDTH_KEY, bounds.width);
		return super.close();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#createContents(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createContents(Composite parent) {
		Control c =  super.createContents(parent);
		commitCommentArea.setFocus();
		return c;
	}
}
