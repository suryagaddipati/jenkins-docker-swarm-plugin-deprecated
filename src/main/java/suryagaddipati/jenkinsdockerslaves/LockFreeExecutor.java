package suryagaddipati.jenkinsdockerslaves;


import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.model.queue.SubTask;
import hudson.model.queue.WorkUnit;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

public class LockFreeExecutor extends Executor {
    private final Queue queue;

    public LockFreeExecutor(@Nonnull final Computer owner, final int n) {
        super(owner, n);
        this.queue = Jenkins.getInstance().getQueue();
    }

    @Override
    public void run() {
        try {
            ACL.impersonate(ACL.SYSTEM);
            final WorkUnit workUnit = getCurrentWorkUnit();
            workUnit.setExecutor(this);
            removePendingFromQueue();
            final SubTask task = workUnit.work;
            final Queue.Executable executable = getExecutable(task);
            JenkinsHacks.setPrivateField(Executor.class, "executable", this, executable);
            workUnit.setExecutable(executable);
            if (executable instanceof Actionable) {
                for (final Action action : workUnit.context.actions) {
                    ((Actionable) executable).addAction(action);
                }
            }

            ACL.impersonate(workUnit.context.item.authenticate());
            setName(getName() + " : executing " + executable.toString());
            this.queue.execute(executable, task);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private Queue.Executable getExecutable(final SubTask task) {
        final Queue.Executable executable;
        try {
            executable = task.createExecutable();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return executable;
    }

    private void removePendingFromQueue() {
        final List pendings = (List) JenkinsHacks.getPrivateField(Queue.class, "pendings", this.queue);
        final WorkUnit wu = getCurrentWorkUnit();
        pendings.remove(wu.context.item);

        final Queue.LeftItem li = new Queue.LeftItem(wu.context);
        for (final QueueListener ql : QueueListener.all()) {
            try {
                ql.onLeft(li);
            } catch (final Throwable e) {
                // don't let this kill the queue
            }
        }
    }
}
