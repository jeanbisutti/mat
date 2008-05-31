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
package org.eclipse.mat.ui.internal.actions;

import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.impl.query.CategoryDescriptor;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.internal.query.browser.QueryBrowserPopup;
import org.eclipse.mat.ui.internal.query.browser.QueryHistory;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.PopupMenu;

public class QueryDropDownMenuAction extends EasyToolBarDropDown
{

    private HeapEditor editor;

    private Action queryBrowser;

    public QueryDropDownMenuAction(HeapEditor editor)
    {
        super("Open Query Browser", MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.QUERY),
                        editor);

        this.editor = editor;

        makeActions();
    }

    private void makeActions()
    {
        queryBrowser = new Action("Search Queries...")
        {
            @Override
            public void run()
            {
                new QueryBrowserPopup(editor).open();
            }
        };
        queryBrowser.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.QUERY));
        queryBrowser.setToolTipText("Search queries by name and description.");
        queryBrowser.setActionDefinitionId("org.eclipse.mat.ui.query.browser.QueryBrowser");
    }

    @Override
    public void contribute(PopupMenu menu)
    {
        addCategorySubMenu(menu, QueryRegistry.instance().getRootCategory());

        menu.addSeparator();

        menu.add(queryBrowser);

        addHistory(menu);
    }

    private void addCategorySubMenu(PopupMenu menu, CategoryDescriptor category)
    {
        for (CategoryDescriptor subCategory : category.getSubCategories())
        {
            PopupMenu categoryItem = new PopupMenu(subCategory.getName());
            menu.add(categoryItem);

            addCategorySubMenu(categoryItem, subCategory);
        }

        for (final QueryDescriptor query : category.getQueries())
        {
            menu.add(new ExecuteQueryAction(editor, query));
        }
    }

    private void addHistory(PopupMenu menu)
    {
        List<String> history = QueryHistory.getHistoryEntries();
        if (!history.isEmpty())
        {
            menu.addSeparator();

            PopupMenu historyMenu = new PopupMenu("History");
            historyMenu.setActionDefinitionId("org.eclipse.mat.ui.query.browser.QueryHistory");
            menu.add(historyMenu);

            int count = 0;
            for (String cmd : history)
            {
                historyMenu.add(new ExecuteQueryAction(editor, cmd));

                if (++count == 10)
                    break;
            }

            Action action = new Action("All...")
            {
                @Override
                public void run()
                {
                    new QueryBrowserPopup(editor, true).open();
                }
            };

            historyMenu.add(action);
        }
    }

}
