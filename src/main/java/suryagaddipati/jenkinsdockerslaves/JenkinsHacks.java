package suryagaddipati.jenkinsdockerslaves;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import jenkins.model.Nodes;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

/*
 Till I can submit patches to Jenkins. currently being discussed on mailing list.
 */
public class JenkinsHacks {
    public static void addNodeWithoutQueueLock(final DockerSlave node) {
        try {
            final Nodes nodes = getNodes();
            nodes.addNode(node);
            final Field nodesMapField = Nodes.class.getDeclaredField("nodes");
            nodesMapField.setAccessible(true);
            final Map<String, Node> nodeMap = (Map<String, Node>) ReflectionUtils.getField(nodesMapField, Jenkins.getInstance());
            nodeMap.put(node.getNodeName(), node);

            final Map<Node, Computer> computers = getComputers();

            final Computer computer = node.createComputer();
            computers.put(node, computer);
            final RetentionStrategy retentionStrategy = computer.getRetentionStrategy();
            if (retentionStrategy != null) {
                // if there is a retention strategy, it is responsible for deciding to start the computer
                retentionStrategy.start(computer);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

    }

    private static Map<Node, Computer> getComputers() throws NoSuchFieldException {
        final Field computersField = Jenkins.class.getDeclaredField("computers");
        computersField.setAccessible(true);
        return (Map<Node, Computer>) ReflectionUtils.getField(computersField, Jenkins.getInstance());
    }

    private static Nodes getNodes() throws NoSuchFieldException {
        final Field nodesField = Jenkins.class.getDeclaredField("nodes");
        nodesField.setAccessible(true);
        return (Nodes) ReflectionUtils.getField(nodesField, Jenkins.getInstance());
    }

    public static void removeNodeWithoutQueueLock(final DockerSlave node) throws IOException {
        try {
            final Nodes nodes = getNodes();
            nodes.removeNode(node);
            getComputers().remove(node);
        } catch (final NoSuchFieldException e) {
            e.printStackTrace();
        }

    }

    public static void setPrivateField(final Class clazz, final String fieldName, final Object target, final Object value) {
        try {
            final Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            ReflectionUtils.setField(field, target, value);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getPrivateField(final Class clazz, final String fieldName, final Object target) {
        try {

            final Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return ReflectionUtils.getField(field, target);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

    }
}
