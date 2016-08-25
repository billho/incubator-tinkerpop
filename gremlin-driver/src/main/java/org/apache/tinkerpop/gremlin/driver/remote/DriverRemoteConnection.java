/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.driver.remote;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.gremlin.process.computer.traversal.strategy.decoration.VertexProgramStrategy;
import org.apache.tinkerpop.gremlin.process.remote.RemoteConnection;
import org.apache.tinkerpop.gremlin.process.remote.RemoteConnectionException;
import org.apache.tinkerpop.gremlin.process.remote.RemoteGraph;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link RemoteConnection} implementation for Gremlin Server. Each {@code DriverServerConnection} is bound to one
 * graph instance hosted in Gremlin Server.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class DriverRemoteConnection implements RemoteConnection {

    public static final String GREMLIN_REMOTE_GRAPH_DRIVER_CLUSTERFILE = "gremlin.remoteGraph.driver.clusterFile";
    public static final String GREMLIN_REMOTE_GRAPH_DRIVER_GRAPHNAME = "gremlin.remoteGraph.driver.graphName";
    private static final String DEFAULT_GRAPH = "graph";
    private static final boolean attachElements = Boolean.valueOf(System.getProperty("is.testing", "false"));

    private final Client client;
    private final boolean tryCloseCluster;
    private final String connectionGraphName;
    private transient Optional<Configuration> conf = Optional.empty();

    public DriverRemoteConnection(final Configuration conf) {
        if (conf.containsKey(GREMLIN_REMOTE_GRAPH_DRIVER_CLUSTERFILE) && conf.containsKey("clusterConfiguration"))
            throw new IllegalStateException(String.format("A configuration should not contain both '%s' and 'clusterConfiguration'", GREMLIN_REMOTE_GRAPH_DRIVER_CLUSTERFILE));

        connectionGraphName = conf.getString(GREMLIN_REMOTE_GRAPH_DRIVER_GRAPHNAME, DEFAULT_GRAPH);

        try {
            final Cluster cluster;
            if (!conf.containsKey(GREMLIN_REMOTE_GRAPH_DRIVER_CLUSTERFILE) && !conf.containsKey("clusterConfiguration"))
                cluster = Cluster.open();
            else
                cluster = conf.containsKey(GREMLIN_REMOTE_GRAPH_DRIVER_CLUSTERFILE) ?
                        Cluster.open(conf.getString(GREMLIN_REMOTE_GRAPH_DRIVER_CLUSTERFILE)) : Cluster.open(conf.subset("clusterConfiguration"));

            client = cluster.connect(Client.Settings.build().unrollTraversers(false).create()).alias(connectionGraphName);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        tryCloseCluster = true;
        this.conf = Optional.of(conf);
    }

    private DriverRemoteConnection(final Cluster cluster, final boolean tryCloseCluster, final String connectionGraphName) {
        client = cluster.connect(Client.Settings.build().unrollTraversers(false).create()).alias(connectionGraphName);
        this.connectionGraphName = connectionGraphName;
        this.tryCloseCluster = tryCloseCluster;
    }

    /**
     * This constructor is largely just for unit testing purposes and should not typically be used externally.
     */
    DriverRemoteConnection(final Cluster cluster, final Configuration conf) {
        connectionGraphName = conf.getString(GREMLIN_REMOTE_GRAPH_DRIVER_GRAPHNAME, DEFAULT_GRAPH);
        client = cluster.connect(Client.Settings.build().unrollTraversers(false).create()).alias(connectionGraphName);
        tryCloseCluster = false;
        this.conf = Optional.of(conf);
    }

    /**
     * Creates a {@link DriverRemoteConnection} from an existing {@link Cluster} instance. When {@link #close()} is
     * called, the {@link Cluster} is left open for the caller to close. By default, this method will bind the
     * {@link RemoteConnection} to a graph on the server named "graph".
     */
    public static DriverRemoteConnection using(final Cluster cluster) {
        return using(cluster, "graph");
    }

    /**
     * Creates a {@link DriverRemoteConnection} from an existing {@link Cluster} instance. When {@link #close()} is
     * called, the {@link Cluster} is left open for the caller to close.
     */
    public static DriverRemoteConnection using(final Cluster cluster, final String connectionGraphName) {
        return new DriverRemoteConnection(cluster, false, connectionGraphName);
    }

    /**
     * Creates a {@link DriverRemoteConnection} using a new {@link Cluster} instance created from the supplied
     * configuration file. When {@link #close()} is called, this new {@link Cluster} is also closed. By default,
     * this method will bind the {@link RemoteConnection} to a graph on the server named "graph".
     */
    public static DriverRemoteConnection using(final String clusterConfFile) {
        return using(clusterConfFile, "graph");
    }

    /**
     * Creates a {@link DriverRemoteConnection} using a new {@link Cluster} instance created from the supplied
     * configuration file. When {@link #close()} is called, this new {@link Cluster} is also closed.
     */
    public static DriverRemoteConnection using(final String clusterConfFile, final String connectionGraphName) {
        try {
            return new DriverRemoteConnection(Cluster.open(clusterConfFile), true, connectionGraphName);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Creates a {@link DriverRemoteConnection} using an Apache {@code Configuration} object. This method of creation
     * is typically used by {@link RemoteGraph} when being constructed via {@link GraphFactory}. The
     * {@code Configuration} object should contain one of two required keys, either: {@code clusterConfigurationFile}
     * or {@code clusterConfiguration}. The {@code clusterConfigurationFile} key is a pointer to a file location
     * containing a configuration for a {@link Cluster}. The {@code clusterConfiguration} should contain the actual
     * contents of a configuration that would be used by a {@link Cluster}.  This {@code configuration} may also
     * contain the optional, but likely necessary, {@code connectionGraphName} which tells the
     * {@code DriverServerConnection} which graph on the server to bind to.
     */
    public static DriverRemoteConnection using(final Configuration conf) {
        if (conf.containsKey("clusterConfigurationFile") && conf.containsKey("clusterConfiguration"))
            throw new IllegalStateException("A configuration should not contain both 'clusterConfigurationFile' and 'clusterConfiguration'");

        if (!conf.containsKey("clusterConfigurationFile") && !conf.containsKey("clusterConfiguration"))
            throw new IllegalStateException("A configuration must contain either 'clusterConfigurationFile' and 'clusterConfiguration'");

        final String connectionGraphName = conf.getString("connectionGraphName", "graph");
        if (conf.containsKey("clusterConfigurationFile"))
            return using(conf.getString("clusterConfigurationFile"), connectionGraphName);
        else {
            return using(Cluster.open(conf.subset("clusterConfiguration")), connectionGraphName);
        }
    }

    @Override
    public <E> Iterator<Traverser.Admin<E>> submit(final Traversal<?, E> t) throws RemoteConnectionException {
        try {
            if (attachElements && !t.asAdmin().getStrategies().toList().stream().filter(s -> s instanceof VertexProgramStrategy).findAny().isPresent()) {
                if (!conf.isPresent()) throw new IllegalStateException("Traverser can't be reattached for testing");
                final Graph graph = ((Supplier<Graph>) conf.get().getProperty("hidden.for.testing.only")).get();
                return new AttachingTraverserIterator<>(client.submit(t).iterator(), graph);
            } else {
                return new TraverserIterator<>(client.submit(t).iterator());
            }
        } catch (Exception ex) {
            throw new RemoteConnectionException(ex);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            client.close();
        } catch (Exception ex) {
            throw new RemoteConnectionException(ex);
        } finally {
            if (tryCloseCluster)
                client.getCluster().close();
        }
    }

    @Override
    public String toString() {
        return "DriverServerConnection-" + client.getCluster() + " [graph=" + connectionGraphName + "]";
    }

    static class TraverserIterator<E> implements Iterator<Traverser.Admin<E>> {

        private Iterator<Result> inner;

        public TraverserIterator(final Iterator<Result> resultIterator) {
            inner = resultIterator;
        }

        @Override
        public boolean hasNext() {
            return inner.hasNext();
        }

        @Override
        public Traverser.Admin<E> next() {
            return (Traverser.Admin<E>) inner.next().getObject();
        }
    }

    static class AttachingTraverserIterator<E> extends TraverserIterator<E> {
        private final Graph graph;

        public AttachingTraverserIterator(final Iterator<Result> resultIterator, final Graph graph) {
            super(resultIterator);
            this.graph = graph;
        }

        @Override
        public Traverser.Admin<E> next() {
            final Traverser.Admin<E> traverser = super.next();
            if (traverser.get() instanceof Attachable && !(traverser.get() instanceof Property))
                traverser.set((E) ((Attachable<Element>) traverser.get()).attach(Attachable.Method.get(graph)));
            return traverser;
        }
    }
}
