package org.eclipse.ui.externaltools.internal.registry;

/**********************************************************************
Copyright (c) 2002 IBM Corp. and others. All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
 
Contributors:
**********************************************************************/

import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPluginRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.externaltools.internal.model.ExternalToolsPlugin;
import org.eclipse.ui.externaltools.internal.model.IExternalToolConstants;

/**
 * General registry reader for external tool variables.
 */
public class ExternalToolVariableRegistry {
	// Format of the variable extension points
	// <extension point="org.eclipse.ui.externalTools.***Variables>
	//		<variable
	//			tag={string}
	//			description={string}
	//			componentClass={string:IVariableComponent}
	//			expanderClass={string:IVariable***Expander}>
	//		</variable>
	// </extension>
	//
	
	/**
	 * Element and attribute tags of a variable extension.
	 */
	/*package*/ static final String TAG_VARIABLE = "variable"; //$NON-NLS-1$
	/*package*/ static final String TAG_TAG = "tag"; //$NON-NLS-1$
	/*package*/ static final String TAG_DESCRIPTION = "description"; //$NON-NLS-1$
	/*package*/ static final String TAG_COMPONENT_CLASS = "componentClass"; //$NON-NLS-1$
	/*package*/ static final String TAG_EXPANDER_CLASS = "expanderClass"; //$NON-NLS-1$


	/**
	 * Sorted map of variables where the key is the variable tag
	 * and the value is the corresponding variable.
	 */
	private SortedMap variables;
	
	/**
	 * The extension point id to read the variables from
	 */
	protected String extensionPointId;
	
	public ExternalToolVariableRegistry() {
		this(IExternalToolConstants.EXTENSION_POINT_TOOL_VARIABLES);
	}
	
	/**
	 * Creates a new registry and loads the variables.
	 */
	protected ExternalToolVariableRegistry(String extensionPointId) {
		this.extensionPointId = extensionPointId;
		loadVariables();
	}

	/**
	 * Returns the variable for the specified tag, or
	 * <code>null</code> if none found.
	 */
	protected final ExternalToolVariable findVariable(String tag) {
		return (ExternalToolVariable) variables.get(tag);
	}

	/**
	 * Returns the number of variables in the registry.
	 */
	public final int getVariableCount() {
		return variables.size();
	}
	
	
	/**
	 * Returns the variable for the given tag or <code>null</code> if none.
	 */
	public ExternalToolVariable getVariable(String tag) {
		return findVariable(tag);
	}
	
	/**
	 * Returns the list of argument variables in the registry.
	 */
	public ExternalToolVariable[] getVariables() {
		ExternalToolVariable[] results = new ExternalToolVariable[getVariableCount()];
		variables.values().toArray(results);
		return results;
	}
	
	/**
	 * Load the available variables
	 */
	private void loadVariables() {
		variables = new TreeMap();
		IPluginRegistry registry = Platform.getPluginRegistry();
		IExtensionPoint point = registry.getExtensionPoint(IExternalToolConstants.PLUGIN_ID, extensionPointId);
		if (point != null) {
			IExtension[] extensions = point.getExtensions();
			for (int i = 0; i < extensions.length; i++) {
				IConfigurationElement[] elements = extensions[i].getConfigurationElements();
				for (int j = 0; j < elements.length; j++) {
					IConfigurationElement element = elements[j];
					if (element.getName().equals(TAG_VARIABLE)) {
						String tag = element.getAttribute(TAG_TAG);
						String description = element.getAttribute(TAG_DESCRIPTION);
						String className = element.getAttribute(TAG_EXPANDER_CLASS);
						
						boolean valid = true;
						if (tag == null || tag.length() == 0) {
							valid = false;
							ExternalToolsPlugin.getDefault().log("Missing tag attribute value for variable element.", null); //$NON-NLS-1$
						}
						if (description == null || description.length() == 0) {
							valid = false;
							ExternalToolsPlugin.getDefault().log("Missing description attribute value for variable element.", null); //$NON-NLS-1$
						}
						if (className == null || className.length() == 0) {
							valid = false;
							ExternalToolsPlugin.getDefault().log("Missing expander class attribute value for variable element.", null); //$NON-NLS-1$
						}

						if (valid)
							variables.put(tag, newVariable(tag, description, element));
					}
				}
			}
		}
	}
	
	/**
	 * Creates a new variable from the specified information.
	 */
	protected ExternalToolVariable newVariable(String tag, String description, IConfigurationElement element) {
		return new ExternalToolVariable(tag, description, element);
	}
	
}
