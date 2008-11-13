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
package org.eclipse.mat.ui.internal.query.arguments;

import java.io.File;

import org.eclipse.core.runtime.Preferences;
import org.eclipse.mat.impl.query.ArgumentDescriptor;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


public class FileOpenDialogEditor extends ArgumentEditor
{
    private static final String LAST_DIRECTORY_KEY = FileOpenDialogEditor.class.getName() + ".lastDir";

    protected Object value;
    protected Text text;
    protected Button openButton;
    protected Composite parent;
    
    private class FileOpenDialogEditorLayout extends Layout
    {
        public void layout(Composite editor, boolean force)
        {
            Rectangle bounds = editor.getClientArea();
            Point size = openButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            if (text != null)
            {
                text.setBounds(0, 0, bounds.width - size.x, bounds.height);
            }
            openButton.setBounds(bounds.width - size.x, 0, size.x, bounds.height);
        }

        public Point computeSize(Composite editor, int wHint, int hHint, boolean force)
        {
            if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT) { return new Point(wHint, hHint); }
            Point contentsSize = text.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            Point buttonSize = openButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, force);
            // Just return the button width to ensure the button is not clipped
            // if the label is long.
            // The label will just use whatever extra width there is
            Point result = new Point(buttonSize.x, Math.max(contentsSize.y, buttonSize.y));
            return result;
        }
    }

    public FileOpenDialogEditor(Composite parent, ArgumentDescriptor descriptor, TableItem item)
    {
        super(parent, descriptor, item);
        this.setBackground(parent.getBackground());
        this.parent = parent;

        createContents();
    }

    protected void createContents()
    {
//        this.setLayout(new FillLayout());
        this.setLayout(new FileOpenDialogEditorLayout());
        text = new Text(this, SWT.LEFT);
        text.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                if (e.character == '\r')
                { // Return key
                    editingDone();
                }
            }
        });

        text.addModifyListener(new ModifyListener()
        {
            // here we verify whether the
            // "Finish" button should be enabled
            public void modifyText(ModifyEvent e)
            {
                editingDone();
            }
        });

        openButton = new Button(this, SWT.NONE);
        openButton.setText("...");
        openButton.addSelectionListener(new SelectionListener()
        {

            public void widgetDefaultSelected(SelectionEvent e)
            {}

            public void widgetSelected(SelectionEvent e)
            {
                Preferences prefs = MemoryAnalyserPlugin.getDefault().getPluginPreferences();
                String lastDirectory = prefs.getString(LAST_DIRECTORY_KEY);

                FileDialog dialog = new FileDialog(getShell(), SWT.OPEN | SWT.MULTI);
                dialog.setText("Choose File...");

                if (lastDirectory != null && lastDirectory.length() > 0)
                    dialog.setFilterPath(lastDirectory);
                else
                    dialog.setFilterPath(System.getProperty("user.home")); //$NON-NLS-1$

                dialog.open();
                String[] names = dialog.getFileNames();
                if (names != null && names.length > 0)
                {
                    final String filterPath = dialog.getFilterPath();
                    prefs.setValue(LAST_DIRECTORY_KEY, filterPath);

                    text.setText(filterPath + File.separator + names[0]);
                }
            }

        });
    }

    protected void editingDone()
    {
        try
        {
            fireErrorEvent(null, this);
            String t = text.getText().trim();

            if (t.length() == 0)
            {
                value = null;
            }
            else
            {
                value = descriptor.stringToValue(t);
            }

            fireValueChangedEvent(value, this);
        }
        catch (SnapshotException e)
        {
            // $JL-EXC$
            fireErrorEvent(e.getMessage(), this);
        }
    }

    @Override
    public void setValue(Object value) throws SnapshotException
    {
        this.value = value;
        text.setText(descriptor.valueToString(value));
    }

    @Override
    public Object getValue()
    {
        return this.value;
    }

    @Override
    public boolean setFocus()
    {
        return text.setFocus();
    }

}