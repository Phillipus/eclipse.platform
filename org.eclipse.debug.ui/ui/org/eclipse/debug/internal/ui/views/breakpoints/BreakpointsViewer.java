/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.breakpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.ui.IBreakpointOrganizerDelegateExtension;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/**
 * Breakpoints viewer.
 */
public class BreakpointsViewer extends CheckboxTreeViewer {
	
    /**
     * Constructs a new breakpoints viewer with the given tree.
     * 
     * @param tree
     */
    public BreakpointsViewer(Tree tree) {
        super(tree);
    }
    
    /**
     * Returns the selected items.
     * 
     * @return seleted items
     */
    public Item[] getSelectedItems() {
        return getSelection(getControl());
    }
    
    /**
     * Returns the item assocaited with the given element, or <code>null</code>.
     * 
     * @param element element in breakpoints view
     * @return item assocaited with the given element, or <code>null</code>
     */
    public Widget searchItem(Object element) {
        return findItem(element);
    }
    
    /**
     * Refreshes the given item in the tree.
     * 
     * @param item item to refresh
     */
    public void refreshItem(TreeItem item) {
        updateItem(item, item.getData());
    }
    
    /**
     * Returns a collection of currently visible breakpoints.
     * 
     * @return collection of currently visible breakpoints
     */
    public IBreakpoint[] getVisibleBreakpoints() {
        IBreakpointManager manager= DebugPlugin.getDefault().getBreakpointManager();
        Object[] elements= ((ITreeContentProvider)getContentProvider()).getElements(manager);
        List list = new ArrayList();
        for (int i = 0; i < elements.length; i++) {
            TreeItem item = (TreeItem) searchItem(elements[i]);
            if (item != null) {
                collectExpandedBreakpoints(item, list);
            }
        }
        return (IBreakpoint[]) list.toArray(new IBreakpoint[list.size()]);
    }

    /**
     * Adds expanded breakpoints to the list. Traverses children of the given
     * tree item if any.
     * 
     * @param item  
     * @param list collection of visible breakpoints
     */
    private void collectExpandedBreakpoints(TreeItem item, List list) {
        Object data = item.getData();
        if (data instanceof IBreakpoint) {
            list.add(data);
            return;
        }
        if (item.getExpanded()) {
            TreeItem[] items = item.getItems();
            for (int i = 0; i < items.length; i++) {
                collectExpandedBreakpoints(items[i], list);
            }
        }
    }

    /**
     * Sets the selection to a specific tree item
     * 
     * @param item
     */
    protected void setSelection(TreeItem item) {
    	getTree().setSelection(new TreeItem[]{item});
    	updateSelection(getSelection());
    }

	/**
     * Returns the container from within the specified path that is the container the breakpoint can be removed from
     * @param breakpoint the breakpoint to get the container for
     * @return the first found container that includes the breakpoint that allows removal, or <code>null</code> if none found
     * @since 3.3
     */
    public BreakpointContainer getRemovableContainer(Item item) {
    	if(item == null) {
    		return null;
    	}
    	if(item.getData() instanceof IBreakpoint) {
	    	TreePath path = getTreePathFromItem(item);
	    	if(path != null) {
		    	IBreakpoint breakpoint = (IBreakpoint) path.getLastSegment();
		    	BreakpointContainer container = null;
		    	for(int i = path.getSegmentCount()-2; i > -1; i--) {
		    		container = (BreakpointContainer) path.getSegment(i);
		    		if(container.contains(breakpoint) && container.getOrganizer().canRemove(breakpoint, container.getCategory())) {
		    			return container;
		    		}
		    	}
	    	}
    	}
    	return null;
    }
	
    /**
     * Returns the addable breakpoint container of the specified breakpoint
     * @param breakpoint the breakpoint to get the container for
     * @return the first found addable container for the specified breakpoint or <code>null</code> if none found
     * @since 3.3
     */
    public BreakpointContainer getAddableContainer(Item item) {
    	TreePath path = getTreePathFromItem(item);
    	if(path != null) {
	    	Object element = path.getLastSegment();
	    	if(element instanceof IBreakpoint) {
		    	BreakpointContainer container = null;
		    	IBreakpoint breakpoint = (IBreakpoint) element;
		    	for(int i = path.getSegmentCount()-2; i > -1; i--) {
		    		container = (BreakpointContainer) path.getSegment(i);
		    		if(container.contains(breakpoint) && container.getOrganizer().canAdd(breakpoint, container.getCategory())) {
		    			return container;
		    		}
		    	}
	    	}
    	}
    	return null;
    }
    
    /**
     * Returns if the selected item in the tree can be dragged
     * <p>
     * Scheme:
     * <ul>
     * <li>breakpoint containers cannot be dragged</li>
     * <li>breakpoints can be dragged iff the container they reside in supports the removal of breakpoints</li>
     * </ul>
     * </p>
     * @param element the element to test if it can be dragged
     * @return true if the selected element can be dragged, false otherwise
     * @since 3.3
     */
    public boolean canDrag(Item[] items) {
    	if(items == null) {
    		return false;
    	}
    	if(items.length == 0) {
    		return false;
    	}
    	for(int i = 0; i < items.length; i++) {
    		if(getRemovableContainer(items[i]) == null) {
    			return false;
    		}
    	}
    	return true;
    }
    
    /**
     * Performs the actual removal of breakpoints from their respective (removable) containers on a successful drag operation
     * @param selection the selection of breakpoints involved in the drag
     * @since 3.3
     */
    public void performDrag(Item[] items) {
    	if(items == null) {
    		return;
    	}
    	Map containersToBreakpoints = new HashMap();
		BreakpointContainer container = null;
    	IBreakpoint breakpoint = null;
    	for(int i = 0; i < items.length; i++) {
    		if(!items[i].isDisposed()) {
	    		breakpoint = (IBreakpoint)items[i].getData();
	    		container = getRemovableContainer(items[i]);
	    		if(container != null) {
	    			List list = (List) containersToBreakpoints.get(container);
	    			if (list == null) {
	    				list = new ArrayList();
	    				containersToBreakpoints.put(container, list);
	    			}
	    			list.add(breakpoint);
	    		}
    		}
    	}
    	Iterator iterator = containersToBreakpoints.entrySet().iterator();
    	while (iterator.hasNext()) {
    		Entry entry = (Entry) iterator.next();
    		container = (BreakpointContainer) entry.getKey();
    		List list = (List) entry.getValue();
    		IBreakpointOrganizer organizer = container.getOrganizer();
    		IBreakpoint[] breakpoints = (IBreakpoint[]) list.toArray(new IBreakpoint[list.size()]);
    		if (organizer instanceof IBreakpointOrganizerDelegateExtension) {
				IBreakpointOrganizerDelegateExtension extension = (IBreakpointOrganizerDelegateExtension) organizer;
				extension.removeBreakpoints(breakpoints, container.getCategory());
			} else {
				for (int i = 0; i < breakpoints.length; i++) {
					organizer.removeBreakpoint(breakpoints[i], container.getCategory());
				}
			}
    	}
    }
    
    /**
     * Determines if the specified element can be dropped into the specified target
     * <p>
     * Scheme:
     * <ul>
     * <li>Breakpoints can be dropped into working sets</li>
     * <li>Breakpoints can be dropped into breakpoints, provided there is a droppable parent of the target breakpoint</li>
     * </ul>
     * </p>
     * @param target the target foor the drop
     * @param element the element we want to drop
     * @return true if the specified element can be dropped into the specified target, false otherwise
     * @since 3.3
     */
    public boolean canDrop(Item target, IStructuredSelection selection) {
    	if(selection == null  || target == null) {
    		return false;
    	}
    	for(Iterator iter = selection.iterator(); iter.hasNext();) {
    		if(!checkAddableParentContainers(target, (IBreakpoint) iter.next())) {
    			return false;
    		}
    	}
    	return true;
    }

	/**
	 * This method is used to determine if there is an addable parent container available for the specified drop target.
	 * <p>
	 * A drop target can be either a <code>BreakpointContainer</code> or an <code>IBreakpoint</code>. This method always checks the entire heirarchy
	 * of the tree path for the specified target in the event one of the parent element does not support dropping. 
	 * </p>
	 * @param target
	 * @param breakpoint
	 * @return
	 */
	private boolean checkAddableParentContainers(Item target, IBreakpoint breakpoint) {
		BreakpointContainer container = null;
		TreePath path = getTreePathFromItem(target);
		if(path != null) {
			Object element = null;
			for(int i = path.getSegmentCount()-1; i > -1; i--) {
				element = path.getSegment(i);
				if(element instanceof BreakpointContainer) {
					container = (BreakpointContainer) element;
					if(container.contains(breakpoint) || !container.getOrganizer().canAdd(breakpoint, container.getCategory())) {
		    			return false;
		    		}
				}
			}
		}
		return true;
	}

	/**
     * Performs the actual addition of the selected breakpoints to the specified target
     * @param target the target to add the selection of breakpoints to
     * @param selection the selection of breakpoints
     * @return true if the drop occurred, false otherwise
     * @since 3.3
     */
    public boolean performDrop(Item target, IStructuredSelection selection) {
		if(target == null || selection == null) {
    		return false;
    	}
    	IBreakpoint breakpoint = null;
    	Object element = target.getData();
    	BreakpointContainer container = (element instanceof BreakpointContainer ? (BreakpointContainer)element : getAddableContainer(target));
    	if(container == null) {
			return false;
		}
    	IBreakpointOrganizer organizer = container.getOrganizer();
    	if (organizer instanceof IBreakpointOrganizerDelegateExtension) {
    		IBreakpointOrganizerDelegateExtension extension = (IBreakpointOrganizerDelegateExtension) organizer;
    		Object[] array = selection.toArray();
    		IBreakpoint[] breakpoints = new IBreakpoint[array.length];
    		System.arraycopy(array, 0, breakpoints, 0, array.length);
    		extension.addBreakpoints(breakpoints, container.getCategory());
    	} else {
	    	for(Iterator iter = selection.iterator(); iter.hasNext();) {
	    		breakpoint = (IBreakpoint) iter.next();
				organizer.addBreakpoint(breakpoint, container.getCategory());
	    	}
    	}
    	expandToLevel(target.getData(), ALL_LEVELS);
    	return true;
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.Viewer#refresh()
	 */
	public void refresh() {
		super.refresh();
		initializeCheckedState();
	}

	/**
	 * Sets the initial checked state of the items in the viewer.
	 */
	private void initializeCheckedState() {
		TreeItem[] items = getTree().getItems();
		for (int i = 0; i < items.length; i++) {
			updateCheckedState(items[i]);
		}
	}    
    
    /**
     * Update the checked state up the given element and all of its children.
     * 
     * @param element
     */
	public void updateCheckedState(Object element) {
        Widget[] widgets = searchItems(element);
        for (int i = 0; i < widgets.length; i++) {
            Widget widget = widgets[i];
            if (widget != null) {
                updateCheckedState((TreeItem)widget);
            }
        }
	}
   
	/**
	 * finds all occurrences of a widget to update
	 * @param element the element to search for when finding occurrences
	 * @return a list of widget occurrences to update or an empty list
	 */
    private Widget[] searchItems(Object element) {
        ArrayList list = new ArrayList();
        TreeItem[] items = getTree().getItems();
        for (int i = 0; i < items.length; i++) {
        	findAllOccurrences(items[i], element, list);
        }
        return (Widget[]) list.toArray(new Widget[0]);
    }
    
    /**
     * performs the actual search for items in the tree
     * @param list the list to add matches to
     * @param item the item in the tree
     * @param element the element to compare
     */
    private void findAllOccurrences(TreeItem item, Object element, ArrayList list) {
        if (element.equals(item.getData())) {
                list.add(item);
        }
        TreeItem[] items = item.getItems();
        for (int i = 0; i < items.length; i++) {
        	findAllOccurrences(items[i], element, list);
        }
    }
    
    /**
     * Update the checked state up the given element and all of its children.
     * 
     * @param element
     */
    public void updateCheckedState(TreeItem item) {
        Object element = item.getData();
        if (element instanceof IBreakpoint) {
            try {
                item.setChecked(((IBreakpoint) element).isEnabled());
                refreshItem(item);
            } catch (CoreException e) {
                DebugUIPlugin.log(e);
            }
        } else if (element instanceof BreakpointContainer) {
            IBreakpoint[] breakpoints = ((BreakpointContainer) element).getBreakpoints();
            int enabledChildren= 0;
            for (int i = 0; i < breakpoints.length; i++) {
                IBreakpoint breakpoint = breakpoints[i];
                try {
                    if (breakpoint.isEnabled()) {
                        enabledChildren++;
                    }
                } catch (CoreException e) {
                    DebugUIPlugin.log(e);
                }
            }
            if (enabledChildren == 0) {
                // Uncheck the container node if no children are enabled
                item.setGrayed(false);
                item.setChecked(false);
            } else if (enabledChildren == breakpoints.length) {
                // Check the container if all children are enabled
                item.setGrayed(false);
                item.setChecked(true);
            } else {
                // If some but not all children are enabled, gray the container node
                item.setGrayed(true);
                item.setChecked(true);
            }
            // Update any children (breakpoints and containers)
            TreeItem[] items = item.getItems();
            for (int i = 0; i < items.length; i++) {
                updateCheckedState(items[i]);
            }
        }
    }        
}
