/*
 * (c) Copyright 2001 MyCorporation.
 * All Rights Reserved.
 */
package org.eclipse.update.ui.forms.internal.engine;

import org.eclipse.swt.graphics.*;

/**
 * @version 	1.0
 * @author
 */
public interface ITextSegment extends IParagraphSegment {
	String getText();
	Color getColor();
	Font getFont();
	boolean isWordWrapAllowed();
	boolean isSelectable();
	boolean contains(int x, int y);
	Rectangle getBounds();
	void paintFocus(GC gc, Color bg, Color fg, boolean selected);
}