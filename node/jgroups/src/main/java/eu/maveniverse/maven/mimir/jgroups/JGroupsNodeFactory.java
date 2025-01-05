package eu.maveniverse.maven.mimir.jgroups;

import eu.maveniverse.maven.mimir.shared.Config;
import eu.maveniverse.maven.mimir.shared.node.LocalNode;
import eu.maveniverse.maven.mimir.shared.node.Node;
import eu.maveniverse.maven.mimir.shared.node.NodeFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;
import org.jgroups.JChannel;

@Singleton
@Named(JGroupsNodeFactory.NAME)
public class JGroupsNodeFactory implements NodeFactory {
    public static final String NAME = "jgroups";

    @Override
    public Optional<Node> createNode(Config config, LocalNode localNode) throws IOException {
        try {
            return Optional.of(new JGroupsNode(localNode, createChannel(config)));
        } catch (Exception e) {
            throw new IOException("Failed to create JChannel", e);
        }
    }

    private JChannel createChannel(Config config) throws Exception {
        return new JChannel("udp-new.xml")
                .name(InetAddress.getLocalHost().getHostName())
                .setDiscardOwnMessages(true)
                .connect("mimir-jgroups");
    }
}
