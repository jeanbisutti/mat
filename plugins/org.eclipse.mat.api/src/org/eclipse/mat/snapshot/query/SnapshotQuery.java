package org.eclipse.mat.snapshot.query;

import java.text.MessageFormat;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.HeapObjectArgumentFactory;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentFactory;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;

public class SnapshotQuery
{

    public static SnapshotQuery lookup(String name, ISnapshot snapshot) throws SnapshotException
    {
        QueryDescriptor query = QueryRegistry.instance().getQuery(name);
        if (query == null)
            throw new SnapshotException(MessageFormat.format("Query not available: {0}", name));

        IQueryContext context = new SnapshotQueryContext(snapshot);
        if (!query.accept(context))
            throw new SnapshotException(query.explain(context));

        ArgumentSet arguments = query.createNewArgumentSet(context);

        return new SnapshotQuery(snapshot, arguments);
    }

    public static SnapshotQuery parse(String commandLine, ISnapshot snapshot) throws SnapshotException
    {
        IQueryContext context = new SnapshotQueryContext(snapshot);
        ArgumentSet arguments = CommandLine.parse(context, commandLine);
        return new SnapshotQuery(snapshot, arguments);
    }

    private final ISnapshot snapshot;
    private final QueryDescriptor query;
    private final ArgumentSet arguments;

    private SnapshotQuery(ISnapshot snapshot, ArgumentSet arguments)
    {
        this.snapshot = snapshot;
        this.query = arguments.getQueryDescriptor();
        this.arguments = arguments;
    }

    public SnapshotQuery set(String name, Object value) throws SnapshotException
    {
        ArgumentDescriptor argument = query.getArgumentByName(name);
        if (argument == null)
            throw new SnapshotException(MessageFormat.format("Unknown argument: {0} for query {1}", name, query
                            .getIdentifier()));

        // special checks: support heap objects

        if ((argument.getType() == int.class && argument.getAdvice() == Argument.Advice.HEAP_OBJECT) //
                        || argument.getType().isAssignableFrom(IObject.class) //
                        || argument.getType().isAssignableFrom(IHeapObjectArgument.class))
        {
            if (value instanceof ArgumentFactory)
            {
                // do nothing: correct
                System.out.println("SnapshotQuery.set()");
            }
            if (value instanceof IObject)
            {
                value = HeapObjectArgumentFactory.build(snapshot, new int[] { ((IObject) value).getObjectId() });
            }
            else if (value instanceof Integer)
            {
                value = HeapObjectArgumentFactory.build(snapshot, new int[] { (Integer) value });
            }
            else if (value instanceof int[])
            {
                value = HeapObjectArgumentFactory.build(snapshot, (int[]) value);
            }
            else if (value instanceof IHeapObjectArgument)
            {
                value = HeapObjectArgumentFactory.build(snapshot, (IHeapObjectArgument) value);
            }
            else
            {
                throw new SnapshotException(MessageFormat.format("Unsupported type for argument {0}: {1}", name, value
                                .getClass().getName()));
            }
        }

        arguments.setArgumentValue(argument, value);

        return this;
    }

    public IResult execute(IProgressListener listener) throws SnapshotException
    {
        QueryResult result = arguments.execute(listener);
        return result != null ? result.getSubject() : null;
    }

    public RefinedResultBuilder refine(IProgressListener listener) throws SnapshotException
    {
        IResult result = execute(listener);
        if (result == null)
            throw new SnapshotException(MessageFormat.format("Query {0} did not produce a result.", arguments
                            .getQueryDescriptor().getIdentifier()));
        return new RefinedResultBuilder(new SnapshotQueryContext(snapshot), (IStructuredResult) result);
    }

}