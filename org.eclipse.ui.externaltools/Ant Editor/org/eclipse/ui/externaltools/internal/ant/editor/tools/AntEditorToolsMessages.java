package org.eclipse.ui.externaltools.internal.ant.editor.tools;

/**********************************************************************
Copyright (c) 2003 IBM Corp. and others. All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
**********************************************************************/

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class AntEditorToolsMessages {

	private static final String BUNDLE_NAME = "org.eclipse.ui.externaltools.internal.ant.editor.tools.AntEditorToolsMessages"; //$NON-NLS-1$

	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

	private AntEditorToolsMessages() {
	}

	public static String getString(String key) {
		try {
			return RESOURCE_BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}
}
