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
package org.eclipse.mat.ui.internal.query.browser;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.mat.impl.query.CategoryDescriptor;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.themes.ColorUtil;


public class QueryBrowserPopup extends PopupDialog
{
    public interface Element
    {
        String getLabel();

        String getUsage();

        QueryDescriptor getQuery();

        ImageDescriptor getImageDescriptor();

        void execute(HeapEditor editor) throws SnapshotException;
    }

    private static final int INITIAL_COUNT_PER_PROVIDER = 5;

    private LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());

    private List<QueryBrowserProvider> providers;
    private HeapEditor editor;

    private Text filterText;
    private QueryContextHelp helpText;
    private Table table;
    private Color grayColor;
    private TextLayout textLayout;

    private boolean resized = false;

    public QueryBrowserPopup(HeapEditor editor)
    {
        this(editor, false);
    }

    public QueryBrowserPopup(HeapEditor editor, boolean onlyHistory)
    {
        super(editor.getEditorSite().getShell(), SWT.RESIZE, true, true, true, true, null,
                        "Start typing to find matches");

        this.editor = editor;

        QueryBrowserPopup.this.providers = new ArrayList<QueryBrowserProvider>();
        providers.add(new QueryHistoryProvider());

        if (!onlyHistory)
            addCategories(QueryRegistry.instance().getRootCategory());

        create();
    }

    private void addCategories(CategoryDescriptor category)
    {
        for (CategoryDescriptor c : category.getSubCategories())
        {
            providers.add(new QueryRegistryProvider(c));
            addCategories(c);
        }
    }

    protected Control createTitleControl(Composite parent)
    {
        filterText = new Text(parent, SWT.NONE);

        GC gc = new GC(parent);
        gc.setFont(parent.getFont());
        FontMetrics fontMetrics = gc.getFontMetrics();
        gc.dispose();

        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).hint(SWT.DEFAULT,
                        Dialog.convertHeightInCharsToPixels(fontMetrics, 1)).applyTo(filterText);

        filterText.addKeyListener(new KeyListener()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == 0x0D)
                {
                    if (e.stateMask != 0)
                        handleSelection(true);
                    else if (filterText.getText().length() == 0)
                        handleSelection(false);
                    else
                        executeFilterText();

                    return;
                }
                else if (e.keyCode == SWT.ARROW_DOWN)
                {
                    int index = table.getSelectionIndex();
                    if (index != -1 && table.getItemCount() > index + 1)
                    {
                        table.setSelection(index + 1);
                        updateHelp();
                    }
                    table.setFocus();
                }
                else if (e.keyCode == SWT.ARROW_UP)
                {
                    int index = table.getSelectionIndex();
                    if (index != -1 && index >= 1)
                    {
                        table.setSelection(index - 1);
                        updateHelp();
                        table.setFocus();
                    }
                }
                else if (e.character == 0x1B) // ESC
                    close();
            }

            public void keyReleased(KeyEvent e)
            {}
        });

        filterText.addModifyListener(new ModifyListener()
        {
            public void modifyText(ModifyEvent e)
            {
                String text = ((Text) e.widget).getText().toLowerCase();
                refresh(text);
            }
        });

        return filterText;
    }

    protected Control createDialogArea(Composite parent)
    {
        Composite composite = (Composite) super.createDialogArea(parent);
        boolean isWin32 = "win32".equals(SWT.getPlatform()); //$NON-NLS-1$
        GridLayoutFactory.fillDefaults().extendedMargins(isWin32 ? 0 : 3, 3, 2, 2).applyTo(composite);

        Composite tableComposite = new Composite(composite, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(tableComposite);

        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        tableComposite.setLayout(tableColumnLayout);
        table = new Table(tableComposite, SWT.SINGLE | SWT.FULL_SELECTION);
        textLayout = new TextLayout(table.getDisplay());
        textLayout.setOrientation(getDefaultOrientation());
        Font boldFont = resourceManager.createFont(FontDescriptor.createFrom(table.getFont()).setStyle(SWT.BOLD));
        textLayout.setFont(table.getFont());
        textLayout.setText("Categories");
        textLayout.setFont(boldFont);

        tableColumnLayout.setColumnData(new TableColumn(table, SWT.NONE), new ColumnWeightData(100, 100));
        table.getShell().addControlListener(new ControlAdapter()
        {
            public void controlResized(ControlEvent e)
            {
                if (!resized)
                {
                    resized = true;
                    e.display.timerExec(100, new Runnable()
                    {
                        public void run()
                        {
                            if (getShell() != null && !getShell().isDisposed())
                            {
                                refresh(filterText.getText().toLowerCase());
                            }
                            resized = false;
                        }
                    });
                }
            }
        });

        table.addKeyListener(new KeyListener()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == SWT.ARROW_UP && table.getSelectionIndex() == 0)
                {
                    filterText.setFocus();
                }
                else if (e.character == SWT.ESC)
                {
                    close();
                }
                else if (e.keyCode == 0x0D && e.stateMask != 0)
                {
                    handleSelection(true);
                }
            }

            public void keyReleased(KeyEvent e)
            {
            // do nothing
            }
        });

        table.addMouseListener(new MouseAdapter()
        {
            public void mouseUp(MouseEvent e)
            {

                if (table.getSelectionCount() < 1)
                    return;

                if (e.button != 1 && e.button != 3)
                    return;

                if (table.equals(e.getSource()))
                {
                    Object o = table.getItem(new Point(e.x, e.y));
                    TableItem selection = table.getSelection()[0];
                    if (selection.equals(o))
                    {
                        if (e.button == 1)
                        {
                            handleSelection(false);
                        }
                        else
                        {
                            handleSelection(true);
                        }
                    }
                }
            }
        });

        table.addSelectionListener(new SelectionListener()
        {
            public void widgetSelected(SelectionEvent e)
            {
                updateHelp();
            }

            public void widgetDefaultSelected(SelectionEvent e)
            {
                handleSelection(false);
            }
        });

        grayColor = resourceManager.createColor(ColorUtil.blend(table.getBackground().getRGB(), table.getForeground()
                        .getRGB()));

        final TextStyle boldStyle = new TextStyle(boldFont, null, null);
        Listener listener = new Listener()
        {
            public void handleEvent(Event event)
            {
                QueryBrowserItem entry = (QueryBrowserItem) event.item.getData();
                if (entry != null)
                {
                    switch (event.type)
                    {
                        case SWT.MeasureItem:
                            entry.measure(event, textLayout, resourceManager, boldStyle);
                            break;
                        case SWT.PaintItem:
                            entry.paint(event, textLayout, resourceManager, boldStyle, grayColor);
                            break;
                        case SWT.EraseItem:
                            entry.erase(event);
                            break;
                    }
                }
                else
                {
                    switch (event.type)
                    {
                        case SWT.MeasureItem:
                            event.height = Math.max(event.height, 16 + 2);
                            event.width = 16;
                            break;
                        case SWT.PaintItem:
                            break;
                        case SWT.EraseItem:
                            break;
                    }
                }
            }
        };
        table.addListener(SWT.MeasureItem, listener);
        table.addListener(SWT.EraseItem, listener);
        table.addListener(SWT.PaintItem, listener);

        // unless one table item is created (and measured) the wrong item height
        // is returned. Therefore more elements are displayed than space is
        // available and - oops - ugly scroll bars appear
        new TableItem(table, SWT.NONE);

        return composite;
    }

    private int computeNumberOfItems()
    {
        int height = table.getClientArea().height;
        int lineWidth = table.getLinesVisible() ? table.getGridLineWidth() : 0;
        return (height - lineWidth) / (table.getItemHeight() + lineWidth);
    }

    private void refresh(String filter)
    {
        int numItems = computeNumberOfItems();

        List<QueryBrowserItem> entries = computeMatchingEntries(filter, numItems);

        refreshTable(entries);

        if (table.getItemCount() > 0)
        {
            table.setSelection(0);
            updateHelp();
        }

        if (filter.length() == 0)
            setInfoText("Start typing to find matches");
        else
            setInfoText("Press Strg-Enter to copy query into input field");
    }

    protected Control getFocusControl()
    {
        return filterText;
    }

    public boolean close()
    {
        if (textLayout != null && !textLayout.isDisposed())
        {
            textLayout.dispose();
        }
        if (resourceManager != null)
        {
            resourceManager.dispose();
            resourceManager = null;
        }
        return super.close();
    }

    protected Point getInitialSize()
    {
        if (!getPersistBounds())
            return new Point(450, 400);
        return super.getInitialSize();
    }

    protected Point getInitialLocation(Point initialSize)
    {
        if (!getPersistBounds())
        {
            Point size = new Point(400, 400);
            Rectangle parentBounds = getParentShell().getBounds();
            int x = parentBounds.x + parentBounds.width / 2 - size.x / 2;
            int y = parentBounds.y + parentBounds.height / 2 - size.y / 2;
            return new Point(x, y);
        }
        return super.getInitialLocation(initialSize);
    }

    protected IDialogSettings getDialogSettings()
    {
        final IDialogSettings workbenchDialogSettings = MemoryAnalyserPlugin.getDefault().getDialogSettings();
        IDialogSettings result = workbenchDialogSettings.getSection(QueryBrowserPopup.class.getName());
        if (result == null)
        {
            result = workbenchDialogSettings.addNewSection(QueryBrowserPopup.class.getName());
        }
        return result;
    }

    @Override
    protected void fillDialogMenu(IMenuManager dialogMenu)
    {
        dialogMenu.add(new Action("Close")
        {
            @Override
            public void run()
            {
                close();
            }
        });
        dialogMenu.add(new Separator());
        super.fillDialogMenu(dialogMenu);
    }

    private void executeFilterText()
    {
        try
        {
            String cmdLine = filterText.getText();

            close();

            QueryExecution.executeCommandLine(editor, null, cmdLine);
        }
        catch (SnapshotException e)
        {
            ErrorHelper.showErrorMessage(e);
        }
    }

    private void handleSelection(boolean doUpdateFilterText)
    {
        if (table.getSelectionCount() != 1)
        {
            close();
            return;
        }

        Element selectedElement = ((QueryBrowserItem) table.getItem(table.getSelectionIndex()).getData()).element;

        if (selectedElement == null)
            return; // do nothing on categories

        if (doUpdateFilterText)
        {
            filterText.setText(selectedElement.getUsage());
            filterText.setSelection(0);
            filterText.setFocus();
        }
        else
        {
            close();

            if (!editor.isDisposed())
            {
                try
                {
                    selectedElement.execute(editor);
                }
                catch (SnapshotException e)
                {
                    ErrorHelper.showErrorMessage(e);
                }
            }
        }
    }

    private void updateHelp()
    {
        if (table.getSelectionCount() > 0)
        {
            Element selectedElement = ((QueryBrowserItem) table.getItem(table.getSelectionIndex()).getData()).element;
            QueryDescriptor query = selectedElement != null ? selectedElement.getQuery() : null;

            if (query != null && query.getHelp() != null)
            {
                if (helpText == null || helpText.getQuery() != query)
                {
                    if (helpText != null)
                        helpText.close();

                    Rectangle myBounds = getShell().getBounds();
                    Rectangle helpBounds = new Rectangle(myBounds.x, myBounds.y + myBounds.height, myBounds.width,
                                    SWT.DEFAULT);
                    helpText = new QueryContextHelp(getShell(), query, helpBounds);
                    helpText.open();
                }

            }
            else
            {
                if (helpText != null)
                    helpText.close();
            }
        }
        else
        {
            if (helpText != null)
                helpText.close();
        }
    }

    private void refreshTable(List<QueryBrowserItem> entries)
    {
        if (table.getItemCount() > entries.size() && table.getItemCount() - entries.size() > 20)
        {
            table.removeAll();
        }
        TableItem[] items = table.getItems();

        int index = 0;

        for (QueryBrowserItem entry : entries)
        {
            TableItem item;
            if (index < items.length)
            {
                item = items[index];
                table.clear(index);
            }
            else
            {
                item = new TableItem(table, SWT.NONE);
            }
            item.setData(entry);

            if (entry.element != null)
                item.setText(0, entry.element.getLabel());
            else
                item.setText(0, entry.provider.getName());
            index++;
        }

        if (index < items.length)
        {
            table.remove(index, items.length - 1);
        }
    }

    private List<QueryBrowserItem> computeMatchingEntries(String filter, int maxCount)
    {
        // first: collect entries on a category level (distribute search
        // results...)

        List<List<QueryBrowserItem>> entries = new ArrayList<List<QueryBrowserItem>>(providers.size());

        int[] indexPerCategory = new int[providers.size()];
        int countPerCategory = Math.min(maxCount / 4, INITIAL_COUNT_PER_PROVIDER);
        int countTotal = 0;

        boolean done = false;

        while (countTotal < maxCount && !done)
        {
            done = true;
            for (int ii = 0; ii < providers.size() && countTotal < maxCount; ii++)
            {
                List<QueryBrowserItem> e = null;

                if (ii == entries.size())
                {
                    entries.add(e = new ArrayList<QueryBrowserItem>());
                    countTotal++; // categories are rendered on one line each
                }
                else
                {
                    e = entries.get(ii);
                }

                int count = 0;
                QueryBrowserProvider provider = providers.get(ii);

                Element[] elements = provider.getElementsSorted();
                int j = indexPerCategory[ii];
                while (j < elements.length && count < countPerCategory && countTotal < maxCount)
                {
                    Element element = elements[j];
                    QueryBrowserItem entry = null;
                    if (filter.length() == 0)
                    {
                        entry = new QueryBrowserItem(element, provider, 0, 0);
                    }
                    else
                    {
                        String sortLabel = element.getLabel();
                        int index = sortLabel.toLowerCase().indexOf(filter);
                        if (index != -1)
                        {
                            entry = new QueryBrowserItem(element, provider, index, index + filter.length() - 1);
                        }
                        else
                        {
                            // test whether category or description match
                            index = provider.getName().toLowerCase().indexOf(filter);
                            if (index == -1 && element.getQuery() != null)
                            {
                                String help = element.getQuery().getHelp();
                                if (help != null)
                                    index = help.toLowerCase().indexOf(filter);
                            }

                            if (index != -1)
                                entry = new QueryBrowserItem(element, provider, 0, 0);
                        }
                    }

                    if (entry != null)
                    {
                        e.add(entry);
                        count++;
                        countTotal++;
                    }
                    j++;
                }
                indexPerCategory[ii] = j;
                if (j < elements.length)
                {
                    done = false;
                }
            }

            countPerCategory = 1;
        }

        // second: convert 'em to a flat list for easy display
        List<QueryBrowserItem> answer = new ArrayList<QueryBrowserItem>();

        for (List<QueryBrowserItem> items : entries)
        {
            if (!items.isEmpty())
            {
                QueryBrowserItem firstElement = items.get(0);
                answer.add(new QueryBrowserItem(null, firstElement.provider, 0, 0));
                answer.addAll(items);
                answer.get(answer.size() - 1).lastInCategory = true;
            }
        }

        return answer;
    }

}