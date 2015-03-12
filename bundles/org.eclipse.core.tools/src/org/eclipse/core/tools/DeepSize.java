/*******************************************************************************
 * Copyright (c) 2003, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * How to use DeepSize:
 * DeepSize result= DeepSize.deepSize(anObject);
 * int size= result.getSize(); // accumulated size of transitive closure of anObject
 * Hashtable sizes= result.getSizes(); // hashtable of internal results: class name-> sum of shallowsize of instances of class
 * Hashtable counts= result.getCounts(); // hashtable of internal results: class name -> instances of class
 * Additional function
 * DeepSize d= new DeepSize();
 * d.setIgnoreTypeNames(aSet); // don't consider instances of classes named in aSet as part of the size
 * d.ignore(anObject); // don't consider anObject as part of the size
 * d.deepCompute(anObject); // advanced compute method - computes the size given the additional ignore configuration
 */
public class DeepSize {
	/**
	 * Used as keys to track sets of non-identical objects.
	 */
	public static class ObjectWrapper {
		private Object object;

		public ObjectWrapper(Object object) {
			this.object = object;
		}

		@Override
		public boolean equals(Object o) {
			if (o.getClass() != ObjectWrapper.class)
				return false;
			return object == ((ObjectWrapper) o).object;
		}

		@Override
		public int hashCode() {
			return object == null ? 1 : object.hashCode();
		}

		@Override
		public String toString() {
			return "ObjectWrapper(" + object + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	public static final int ARRAY_HEADER_SIZE = 12;

	public static final int HEADER_SIZE = 8;
	static final HashSet ignoreSet = new HashSet();
	public static final int OBJECT_HEADER_SIZE = HEADER_SIZE;
	public static final int POINTER_SIZE = 4;
	int byteSize;
	final Map counts = new HashMap();

	Set ignoreTypeNames = null;
	final Map sizes = new HashMap();

	/**
	 * Adds an object to the ignore set. Returns true if the object
	 * has already been ignored previously, and false otherwise.
	 */
	public static boolean ignore(Object o) {
		return !ignoreSet.add(new ObjectWrapper(o));
	}

	public static void reset() {
		ignoreSet.clear();
	}

	private void count(Class c, int size) {
		Object accumulatedSizes = sizes.get(c);
		int existingSize = (accumulatedSizes == null) ? 0 : ((Integer) accumulatedSizes).intValue();
		sizes.put(c, new Integer(existingSize + size));

		Object accumulatedCounts = counts.get(c);
		int existingCount = (accumulatedCounts == null) ? 0 : ((Integer) accumulatedCounts).intValue();
		counts.put(c, new Integer(existingCount + 1));
	}

	public void deepSize(Object o) {
		byteSize += sizeOf(o);
	}

	public Map getCounts() {
		return counts;
	}

	Set getDefaultIgnoreTypeNames() {
		Set ignored = new HashSet();
		String[] ignore = {"org.eclipse.core.runtime.Plugin", "java.lang.ClassLoader", "org.eclipse.team.internal.ccvs.core.CVSTeamProvider", "org.eclipse.core.internal.events.BuilderPersistentInfo", "org.eclipse.core.internal.resources.Workspace", "org.eclipse.core.internal.events.EventStats", "java.net.URL"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-6$
		for (int i = 0; i < ignore.length; i++) {
			ignored.add(ignore[i]);
		}
		return ignored;
	}

	private Object getFieldObject(Field f, Object o) {
		try {
			f.setAccessible(true);
			return f.get(o);
		} catch (IllegalAccessException e) {
			throw new Error(e.toString());
		}
	}

	public int getSize() {
		return byteSize;
	}

	public Map getSizes() {
		return sizes;
	}

	private boolean isStaticField(Field f) {
		return (Modifier.STATIC & f.getModifiers()) != 0;
	}

	/**
	 * Prints a detailed report of memory usage by type to standard output
	 */
	public void printSizeReport() {
		System.out.println("*** Begin DeepSize report ***"); //$NON-NLS-1$
		for (Iterator it = sizes.keySet().iterator(); it.hasNext();) {
			Class clazz = (Class) it.next();
			int size = ((Integer) sizes.get(clazz)).intValue();
			System.out.println('\t' + clazz.getName() + " size: " + size); //$NON-NLS-1$
			System.out.println("Total size of all objects: " + getSize()); //$NON-NLS-1$
		}
		System.out.println("*** End DeepSize report ***"); //$NON-NLS-1$
	}

	void setIgnoreTypeNames(Set ignore) {
		ignoreTypeNames = ignore;
	}

	private boolean shouldIgnoreType(Class clazz) {
		if (ignoreTypeNames == null) {
			ignoreTypeNames = getDefaultIgnoreTypeNames();
		}
		while (clazz != null) {
			if (ignoreTypeNames.contains(clazz.getName()))
				return true;
			clazz = clazz.getSuperclass();
		}
		return false;
	}

	private int sizeOf(Object o) {
		if (o == null)
			return 0;
		if (ignore(o))
			return 0;
		Class clazz = o.getClass();
		if (shouldIgnoreType(clazz))
			return 0;
		return clazz.isArray() ? sizeOfArray(clazz, o) : sizeOfObject(clazz, o);
	}

	private int sizeOfArray(Class type, Object array) {

		int size = ARRAY_HEADER_SIZE;
		Class componentType = type.getComponentType();
		if (componentType.isPrimitive()) {

			if (componentType == char.class) {
				char[] a = (char[]) array;
				size += a.length * 2;
			} else if (componentType == int.class) {
				int[] a = (int[]) array;
				size += a.length * 4;
			} else if (componentType == byte.class) {
				byte[] a = (byte[]) array;
				size += a.length;
			} else if (componentType == boolean.class) {
				//TODO representation of a boolean array might be optimized by JVM
				boolean[] a = (boolean[]) array;
				size += a.length;
			} else if (componentType == short.class) {
				short[] a = (short[]) array;
				size += a.length * 2;
			} else if (componentType == long.class) {
				long[] a = (long[]) array;
				size += a.length * 8;
			} else {
				//TODO: primitive arrays
			}
			count(type, size);
			return size;
		}
		Object[] a = (Object[]) array;
		for (int i = 0; i < a.length; i++) {
			size += POINTER_SIZE + sizeOf(a[i]);
		}
		count(type, ARRAY_HEADER_SIZE + POINTER_SIZE * a.length);
		return size;

	}

	private int sizeOfObject(Class type, Object o) {

		int internalSize = 0; // size of referenced objects
		int shallowSize = OBJECT_HEADER_SIZE;
		Class clazz = type;
		while (clazz != null) {
			Field[] fields = clazz.getDeclaredFields();
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (!isStaticField(f)) {
					Class fieldType = f.getType();
					if (fieldType.isPrimitive()) {
						shallowSize += sizeOfPrimitiveField(fieldType);
					} else {
						shallowSize += POINTER_SIZE;
						internalSize += sizeOf(getFieldObject(f, o));
					}
				}
			}
			clazz = clazz.getSuperclass();
		}
		count(type, shallowSize);
		return shallowSize + internalSize;

	}

	private int sizeOfPrimitiveField(Class type) {
		if (type == long.class || type == double.class)
			return 8;
		return 4;
	}

}
