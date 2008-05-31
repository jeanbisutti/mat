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
package org.eclipse.mat.impl.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.test.SectionSpec;
import org.eclipse.mat.test.Spec;
import org.eclipse.mat.test.ITestResult.Status;
import org.eclipse.mat.util.IProgressListener;


public class SectionPart extends AbstractPart
{
    private List<AbstractPart> children;

    /* package */SectionPart(SectionPart parent, SectionSpec spec)
    {
        super(parent, spec);

        this.status = spec.getStatus();
        
        this.children = new ArrayList<AbstractPart>(spec.getChildren().size());
        for (Spec child : spec.getChildren())
            children.add(AbstractPart.build(this, child));
    }

    @Override
    public Status execute(ISnapshot snapshot, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException
    {
        renderer.beginSection(this);

        // this list is dynamically changed while iterating over it. Therefore
        // we cannot use an iterator -> ConcurrentModificationException
        for (int ii = 0; ii < this.children.size(); ii++)
        {
            AbstractPart part = this.children.get(ii);
            Status status = part.execute(snapshot, renderer, listener);
            this.status = Status.max(this.status, status);
        }

        renderer.endSection(this);

        return status;
    }

    public List<AbstractPart> getChildren()
    {
        return children;
    }

    public void replace(AbstractPart part, AbstractPart other)
    {
        this.children.set(this.children.indexOf(part), other);
    }

}
