/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.io.Serializable;

/**
 * Describes a garbage collection root.
 */
abstract public class GCRootInfo implements Serializable
{
    private static final long serialVersionUID = 2L;

    /**
     * Reasons why an heap object is a garbage collection root.
     */
    public interface Type
    {
        int UNKNOWN = 1;
        /**
         * Class loaded by system class loader, e.g. java.lang.String
         */
        int SYSTEM_CLASS = 2;
        /**
         * Local variable in native code
         */
        int NATIVE_LOCAL = 4;
        /**
         * Global variable in native code
         */
        int NATIVE_STATIC = 8;
        /**
         * Started but not stopped threads
         */
        int THREAD_BLOCK = 16;
        /**
         * Everything you have called wait() or notify() on or you have
         * synchronized on
         */
        int BUSY_MONITOR = 32;
        /**
         * Local variable, i.e. method input parameters or locally created
         * objects of methods still on the stack of a thread
         */
        int JAVA_LOCAL = 64;
        /**
         * In or out parameters in native code; frequently seen as some methods
         * have native parts and the objects handled as method parameters become
         * GC roots, e.g. parameters used for file/network I/O methods or
         * reflection
         */
        int NATIVE_STACK = 128;
        int THREAD_OBJ = 256;
    }

    private final static String[] TYPE_STRING = new String[] { "Unknown", "System Class", "JNI Local", "JNI Global",
                    "Thread Block", "Busy Monitor", "Java Local", "Native Stack", "Thread" };

    protected int objectId;
    private long objectAddress;
    protected int contextId;
    private long contextAddress;
    private int type;

    public GCRootInfo(long objectAddress, long contextAddress, int type)
    {
        this.objectAddress = objectAddress;
        this.contextAddress = contextAddress;
        this.type = type;
    }

    public int getObjectId()
    {
        return objectId;
    }

    public long getObjectAddress()
    {
        return objectAddress;
    }

    public long getContextAddress()
    {
        return contextAddress;
    }

    public int getContextId()
    {
        return contextId;
    }

    public int getType()
    {
        return type;
    }

    public static String getTypeSetAsString(GCRootInfo[] roots)
    {
        int typeSet = 0;
        for (GCRootInfo info : roots)
        {
            typeSet |= info.getType();
        }

        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < 9; i++)
        {
            if (((1 << i) & typeSet) != 0)
            {
                if (!first)
                {
                    buf.append(", ");
                }
                else
                {
                    first = false;
                }
                buf.append(TYPE_STRING[i]);
            }
        }
        return buf.toString();

    }

}
