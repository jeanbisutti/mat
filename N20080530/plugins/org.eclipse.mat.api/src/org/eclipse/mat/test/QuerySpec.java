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
package org.eclipse.mat.test;

import java.text.MessageFormat;

import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;


public class QuerySpec extends Spec
{
    private String command;
    private IResult result;

    public QuerySpec()
    {}

    public QuerySpec(String name)
    {
        super(name);
    }

    public QuerySpec(Class<? extends IQuery> query)
    {
        this(query, null);
    }

    public QuerySpec(Class<? extends IQuery> queryClass, String arguments)
    {
        super("");

        QueryDescriptor descriptor = QueryRegistry.instance().getQuery(queryClass);
        if (descriptor == null)
            throw new RuntimeException(MessageFormat.format("Query {0} not registered. Location known to Analyzer?",
                            queryClass.getName()));

        setName(descriptor.getName());

        if (arguments == null)
            this.command = descriptor.getIdentifier();
        else
            this.command = descriptor.getIdentifier() + " " + arguments;
    }

    public QuerySpec(String name, IResult result)
    {
        super(name);
        this.result = result;
    }

    public String getCommand()
    {
        return command;
    }

    public void setCommand(String query)
    {
        this.command = query;
    }

    public IResult getResult()
    {
        return result;
    }

    public void setResult(IResult result)
    {
        this.result = result;
    }

    @Override
    public void merge(Spec other)
    {
        if (!(other instanceof QuerySpec))
            throw new RuntimeException(MessageFormat.format("Incompatible types: {0} and {1}", other.getName(),
                            getName()));

        super.merge(other);
        if (command == null)
            command = ((QuerySpec) other).command;

        if (result == null)
            result = ((QuerySpec) other).result;
    }

}
