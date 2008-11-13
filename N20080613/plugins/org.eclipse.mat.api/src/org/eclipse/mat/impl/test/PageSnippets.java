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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.eclipse.mat.impl.query.DiggerUrl;
import org.eclipse.mat.impl.test.ResultRenderer.HtmlArtefact;
import org.eclipse.mat.test.Params;


/* package */class PageSnippets
{

    public static void beginPage(HtmlArtefact parent, HtmlArtefact artefact, String title) throws IOException
    {
        artefact.append("<html><head>");
        artefact.append("<title>").append(title).append("</title>");
        artefact.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\">");
        artefact.append("<script src=\"code.js\" type=\"text/javascript\"></script>");
        artefact.append("</head><body onload=\"preparepage();\">");

        artefact.append("<div id=\"toc\"><ul>");
        artefact.append("<li><a href=\"index.html\">Home</a></li>");
        artefact.append("<li><a href=\"toc.html\">Table Of Contents</a></li>");

        if (parent != null)
            artefact.append("<li><a href=\"").append(parent.file.getName()).append("\">Up</a></li>");

        artefact.append("</ul></div>\n");
    }

    public static void endPage(HtmlArtefact artefact) throws IOException
    {
        artefact.append("</body></html>");
    }

    public static void heading(HtmlArtefact artefact, AbstractPart part, int order)
    {
        boolean showHeading = part.params().shallow().getBoolean(Params.Html.SHOW_HEADING, true);
        if (!showHeading)
        {
            artefact.append("<a name=\"").append(part.getId()).append("\"/>");
        }
        else
        {
            String v = String.valueOf(order);
            artefact.append("<h").append(v).append(">");

            if (part.getStatus() != null)
                artefact.append("<img src=\"img/").append(part.getStatus().name().toLowerCase() + ".gif\"> ");

            artefact.append("<a name=\"").append(part.getId()).append("\">");
            artefact.append(part.spec().getName());
            artefact.append("</a></h").append(v).append(">");
        }
    }

    public static void linkedHeading(HtmlArtefact artefact, AbstractPart part, int order, String target)
    {
        String v = String.valueOf(order);
        artefact.append("<h").append(v).append(">");

        if (part instanceof QueryPart && part.getStatus() != null)
            artefact.append("<img src=\"img/").append(part.getStatus().name().toLowerCase() + ".gif\"> ");

        artefact.append("<a href=\"").append(target).append("\">");
        artefact.append(part.spec().getName());
        artefact.append("</a></h").append(v).append(">");
    }

    public static void queryHeading(HtmlArtefact artefact, QueryPart query)
    {
        boolean showHeading = query.params().shallow().getBoolean(Params.Html.SHOW_HEADING, true);

        if (!showHeading)
        {
            artefact.append("<a name=\"").append(query.getId()).append("\"/>");
        }
        else
        {
            artefact.append("<h5>");

            if (query.getStatus() != null)
                artefact.append("<img src=\"img/").append(query.getStatus().name().toLowerCase() + ".gif\"> ");

            artefact.append("<a name=\"").append(query.getId()).append("\">");
            artefact.append(query.spec().getName()).append("</a>");
            artefact.append(" <a href=\"#\" onclick=\"hide('exp").append(query.getId()).append(
                            "'); return false;\" title=\"hide / unhide\"><img src=\"img/hide.gif\"></a>");

            if (query.getCommand() != null)
            {
                String cmdString = null;

                try
                {
                    cmdString = URLEncoder.encode(query.getCommand(), "UTF-8");
                }
                catch (UnsupportedEncodingException e)
                {
                    // $JL-EXC$
                    // should never happen as UTF-8 is always supported
                    cmdString = query.getCommand();
                }

                artefact.append("<a href=\"").append(DiggerUrl.forQuery(query.getCommand())).append(
                                "\" title=\"Open in Memory Analyzer: ").append(cmdString).append(
                                "\"><img src=\"img/open.gif\"></a>");
            }

            artefact.append("</h5>");
        }
    }

    public static void link(HtmlArtefact artefact, String target, String label)
    {
        artefact.append("<a href=\"").append(target).append("\">");
        artefact.append(label);
        artefact.append("</a>");
    }

    public static void beginLink(HtmlArtefact artefact, String target)
    {
        artefact.append("<a href=\"").append(target).append("\">");
    }

    public static void endLink(HtmlArtefact artefact)
    {
        artefact.append("</a>");
    }

    public static void beginExpandableDiv(HtmlArtefact artefact, AbstractPart part)
    {
        artefact.append("<div id=\"exp").append(part.getId()).append("\"");
        if (part.params().getBoolean(Params.Html.COLLAPSED, false))
            artefact.append(" style=\"display:none\"");
        artefact.append(">");
    }

    public static void endDiv(HtmlArtefact artefact)
    {
        artefact.append("</div>");
    }

}