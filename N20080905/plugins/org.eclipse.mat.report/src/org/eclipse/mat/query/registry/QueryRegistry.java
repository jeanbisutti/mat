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
package org.eclipse.mat.query.registry;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Usage;
import org.eclipse.mat.report.internal.ReportPlugin;
import org.eclipse.mat.util.RegistryReader;

public class QueryRegistry extends RegistryReader<IQuery>
{
    private final Map<String, QueryDescriptor> commandsByIdentifier = new HashMap<String, QueryDescriptor>();
    private final Map<String, QueryDescriptor> commandsByClass = new HashMap<String, QueryDescriptor>();

    private CategoryDescriptor rootCategory;

    private static final QueryRegistry instance = new QueryRegistry();

    public static QueryRegistry instance()
    {
        return instance;
    }

    public QueryRegistry()
    {
        init(ReportPlugin.getDefault().getExtensionTracker(), ReportPlugin.PLUGIN_ID + ".query");
    }

    @Override
    protected IQuery createDelegate(IConfigurationElement configElement) throws CoreException
    {
        try
        {
            IQuery query = (IQuery) configElement.createExecutableExtension("impl");
            QueryDescriptor descriptor = registerQuery(query);

            if (ReportPlugin.getDefault().isDebugging())
                ReportPlugin.log(IStatus.INFO, MessageFormat.format("Query registered: {0}", descriptor));

            rootCategory = null;
            return descriptor != null ? query : null;
        }
        catch (SnapshotException e)
        {
            throw new CoreException(new Status(IStatus.ERROR, ReportPlugin.PLUGIN_ID, MessageFormat.format(
                            "Error registering query: {0}", configElement.getAttribute("impl")), e));
        }
    }

    @Override
    protected void removeDelegate(IQuery delegate)
    {
        for (QueryDescriptor descriptor : commandsByIdentifier.values())
        {
            if (descriptor.getCommandType() == delegate.getClass())
            {
                commandsByIdentifier.remove(descriptor.getIdentifier());
                commandsByClass.remove(descriptor.getCommandType().getName().toLowerCase());
                rootCategory = null;
                break;
            }
        }
    }

    public synchronized Collection<QueryDescriptor> getQueries()
    {
        return Collections.unmodifiableCollection(commandsByIdentifier.values());
    }

    public List<QueryDescriptor> getQueries(Pattern pattern)
    {
        List<QueryDescriptor> answer = new ArrayList<QueryDescriptor>();

        for (Map.Entry<String, QueryDescriptor> entry : commandsByIdentifier.entrySet())
        {
            if (pattern.matcher(entry.getKey()).matches())
                answer.add(entry.getValue());
        }

        return answer;
    }

    public synchronized CategoryDescriptor getRootCategory()
    {
        if (rootCategory == null)
        {
            LinkedList<QueryDescriptor> stack = new LinkedList<QueryDescriptor>();
            stack.addAll(commandsByIdentifier.values());

            rootCategory = new CategoryDescriptor("<root>");
            while (!stack.isEmpty())
            {
                QueryDescriptor descriptor = stack.removeFirst();
                stack.addAll(descriptor.getMenuEntries());

                String category = descriptor.getCategory();
                if (Category.HIDDEN.equals(category))
                    continue;

                if (category == null)
                {
                    rootCategory.add(descriptor);
                }
                else
                {
                    CategoryDescriptor entry = rootCategory.resolve(category);
                    entry.add(descriptor);
                }
            }
        }

        return rootCategory;
    }

    public synchronized QueryDescriptor getQuery(String name)
    {
        QueryDescriptor descriptor = commandsByIdentifier.get(name);
        return descriptor != null ? descriptor : commandsByClass.get(name);
    }

    public synchronized QueryDescriptor getQuery(Class<? extends IQuery> query)
    {
        return commandsByClass.get(query.getName().toLowerCase());
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    private String getIdentifier(IQuery query)
    {
        Class<? extends IQuery> queryClass = query.getClass();

        Name n = queryClass.getAnnotation(Name.class);
        String name = n != null ? n.value() : queryClass.getSimpleName();

        CommandName cn = queryClass.getAnnotation(CommandName.class);
        return (cn != null ? cn.value() : name).toLowerCase().replace(' ', '_');
    }

    private final synchronized QueryDescriptor registerQuery(IQuery query) throws SnapshotException
    {
        Class<? extends IQuery> queryClass = query.getClass();

        Name n = queryClass.getAnnotation(Name.class);
        String name = n != null ? n.value() : queryClass.getSimpleName();

        String identifier = getIdentifier(query);

        // do NOT overwrite command names
        if (commandsByIdentifier.containsKey(identifier))
            throw new SnapshotException(MessageFormat.format("Query name ''{0}'' is already bound to {1}!", identifier,
                            commandsByIdentifier.get(identifier).getCommandType().getName()));

        Category c = queryClass.getAnnotation(Category.class);
        String category = c != null ? c.value() : null;

        Usage u = queryClass.getAnnotation(Usage.class);
        String usage = u != null ? u.value() : null;

        Help h = queryClass.getAnnotation(Help.class);
        String help = h != null ? h.value() : null;

        Icon i = queryClass.getAnnotation(Icon.class);
        URL icon = i != null ? queryClass.getResource(i.value()) : null;

        QueryDescriptor descriptor = new QueryDescriptor(identifier, name, category, queryClass, usage, icon, help);

        Class<?> clazz = queryClass;
        while (!clazz.equals(Object.class))
        {
            addArguments(query, clazz, descriptor);
            clazz = clazz.getSuperclass();
        }

        readMenuEntries(queryClass, descriptor);

        commandsByIdentifier.put(identifier, descriptor);
        commandsByClass.put(query.getClass().getName().toLowerCase(), descriptor);
        return descriptor;
    }

    private void readMenuEntries(Class<? extends IQuery> queryClass, QueryDescriptor descriptor)
    {
        Menu menu = queryClass.getAnnotation(Menu.class);
        if (menu == null || menu.value() == null || menu.value().length == 0)
            return;

        for (Menu.Entry entry : menu.value())
        {
            String label = entry.label();

            String category = entry.category();
            if (category.length() == 0)
                category = descriptor.getCategory();

            String help = entry.help();
            if (help.length() == 0)
                help = descriptor.getHelp();

            URL icon = descriptor.getIcon();
            String i = entry.icon();
            if (i.length() > 0)
                icon = queryClass.getResource(i);

            String options = entry.options();

            descriptor.addMenuEntry(label, category, help, icon, options);
        }
    }

    private void addArguments(IQuery query, Class<?> clazz, QueryDescriptor descriptor) throws SnapshotException
    {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields)
        {
            try
            {
                Argument argument = field.getAnnotation(Argument.class);

                if (argument != null)
                {
                    ArgumentDescriptor argDescriptor = fromAnnotation(clazz, argument, field, field.get(query));

                    // add help (if available)
                    Help help = field.getAnnotation(Help.class);
                    if (help != null)
                        argDescriptor.setHelp(help.value());

                    descriptor.addParamter(argDescriptor);
                }
            }
            catch (SnapshotException e)
            {
                throw e;
            }
            catch (IllegalAccessException e)
            {
                String msg = "Unable to access argument ''{0}'' of class ''{1}''. Make sure the attribute is PUBLIC.";
                throw new SnapshotException(MessageFormat.format(msg, field.getName(), clazz.getName()), e);
            }
            catch (Exception e)
            {
                throw new SnapshotException(MessageFormat.format("Error get argument ''{0}'' of class ''{1}''", field
                                .getName(), clazz.getName()), e);
            }
        }
    }

    private ArgumentDescriptor fromAnnotation(Class<?> clazz, Argument annotation, Field field, Object defaultValue)
                    throws SnapshotException
    {
        ArgumentDescriptor d = new ArgumentDescriptor();
        d.setMandatory(annotation.isMandatory());
        d.setName(field.getName());

        String flag = annotation.flag();
        if (flag.length() == 0)
            flag = field.getName().toLowerCase();
        if ("none".equals(flag))
            flag = null;
        d.setFlag(flag);

        d.setField(field);

        d.setArray(field.getType().isArray());
        d.setList(List.class.isAssignableFrom(field.getType()));

        // set type of the argument
        if (d.isArray())
        {
            d.setType(field.getType().getComponentType());
        }
        else if (d.isList())
        {
            Type type = field.getGenericType();
            if (type instanceof ParameterizedType)
            {
                Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
                d.setType((Class<?>) typeArguments[0]);
            }
        }
        else
        {
            d.setType(field.getType());
        }

        // validate the advice
        Argument.Advice advice = annotation.advice();

        if (advice == Argument.Advice.CLASS_NAME_PATTERN && !Pattern.class.isAssignableFrom(d.getType()))
        {
            String msg = MessageFormat.format("Field {0} of {1} has advice {2} but is not of type {3}.", field
                            .getName(), clazz.getName(), Argument.Advice.CLASS_NAME_PATTERN, Pattern.class.getName());
            throw new SnapshotException(msg);
        }

        if (advice != Argument.Advice.NONE)
            d.setAdvice(advice);

        // set the default value
        if (d.isArray() && defaultValue != null)
        {
            // internally, all multiple values have their values held as arrays
            // therefore we convert the array once and for all
            int size = Array.getLength(defaultValue);
            List<Object> l = new ArrayList<Object>(size);
            for (int ii = 0; ii < size; ii++)
            {
                l.add(Array.get(defaultValue, ii));
            }
            d.setDefaultValue(Collections.unmodifiableList(l));
        }
        else
        {
            d.setDefaultValue(defaultValue);
        }

        return d;
    }
}