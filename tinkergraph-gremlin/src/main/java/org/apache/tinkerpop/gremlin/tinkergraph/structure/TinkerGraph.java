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
package org.apache.tinkerpop.gremlin.tinkergraph.structure;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.process.computer.TinkerGraphComputer;
import org.apache.tinkerpop.gremlin.tinkergraph.process.computer.TinkerGraphView;
import org.apache.tinkerpop.gremlin.tinkergraph.process.traversal.strategy.optimization.TinkerGraphStepStrategy;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * An in-sideEffects, reference implementation of the property graph interfaces provided by Gremlin3.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_PERFORMANCE)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_COMPUTER)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_PERFORMANCE)
@Graph.OptIn(Graph.OptIn.SUITE_GROOVY_PROCESS_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_GROOVY_PROCESS_COMPUTER)
@Graph.OptIn(Graph.OptIn.SUITE_GROOVY_ENVIRONMENT)
@Graph.OptIn(Graph.OptIn.SUITE_GROOVY_ENVIRONMENT_INTEGRATE)
@Graph.OptIn(Graph.OptIn.SUITE_GROOVY_ENVIRONMENT_PERFORMANCE)
public class TinkerGraph implements Graph {

    static {
        TraversalStrategies.GlobalCache.registerStrategies(TinkerGraph.class, TraversalStrategies.GlobalCache.getStrategies(Graph.class).clone().addStrategies(TinkerGraphStepStrategy.instance()));
    }

    private static final Configuration EMPTY_CONFIGURATION = new BaseConfiguration() {{
        this.setProperty(Graph.GRAPH, TinkerGraph.class.getName());
    }};

    public static final String CONFIG_VERTEX_ID = "tinkergraph.vertex.id";
    public static final String CONFIG_EDGE_ID = "tinkergraph.edge.id";
    public static final String CONFIG_VERTEX_PROPERTY_ID = "tinkergraph.vertex-property.id";

    protected Long currentId = -1l;
    protected Map<Object, Vertex> vertices = new ConcurrentHashMap<>();
    protected Map<Object, Edge> edges = new ConcurrentHashMap<>();

    protected TinkerGraphVariables variables = null;
    protected TinkerGraphView graphView = null;
    protected TinkerIndex<TinkerVertex> vertexIndex = null;
    protected TinkerIndex<TinkerEdge> edgeIndex = null;

    private final static TinkerGraph EMPTY_GRAPH = new TinkerGraph(EMPTY_CONFIGURATION);

    protected IdManager<?> vertexIdManager;
    protected IdManager<?> edgeIdManager;
    protected IdManager<?> vertexPropertyIdManager;

    private final Configuration configuration;

    /**
     * An empty private constructor that initializes {@link TinkerGraph}.
     */
    private TinkerGraph(final Configuration configuration) {
        this.configuration = configuration;

        final String vertexIdManagerConfigValue = configuration.getString(CONFIG_VERTEX_ID, DefaultIdManager.ANY.name());
        try {
            vertexIdManager = DefaultIdManager.valueOf(vertexIdManagerConfigValue);
        } catch (IllegalArgumentException iae) {
            try {
                vertexIdManager = (IdManager) Class.forName(vertexIdManagerConfigValue).newInstance();
            } catch (Exception ex) {
                throw new IllegalStateException(String.format("Could not configure TinkerGraph vertex id manager with %s", vertexIdManagerConfigValue));
            }
        }

        final String edgeIdManagerConfigValue = configuration.getString(CONFIG_EDGE_ID, DefaultIdManager.ANY.name());
        try {
            edgeIdManager = DefaultIdManager.valueOf(edgeIdManagerConfigValue);
        } catch (IllegalArgumentException iae) {
            try {
                edgeIdManager = (IdManager) Class.forName(edgeIdManagerConfigValue).newInstance();
            } catch (Exception ex) {
                throw new IllegalStateException(String.format("Could not configure TinkerGraph edge id manager with %s", edgeIdManagerConfigValue));
            }
        }

        final String vertexPropIdManagerConfigValue = configuration.getString(CONFIG_VERTEX_PROPERTY_ID, DefaultIdManager.ANY.name());
        try {
            vertexPropertyIdManager = DefaultIdManager.valueOf(vertexPropIdManagerConfigValue);
        } catch (IllegalArgumentException iae) {
            try {
                vertexPropertyIdManager = (IdManager) Class.forName(vertexPropIdManagerConfigValue).newInstance();
            } catch (Exception ex) {
                throw new IllegalStateException(String.format("Could not configure TinkerGraph vertex property id manager with %s", vertexPropIdManagerConfigValue));
            }
        }
    }

    public static TinkerGraph empty() {
        return EMPTY_GRAPH;
    }

    /**
     * Open a new {@link TinkerGraph} instance.
     * <p/>
     * <b>Reference Implementation Help:</b> If a {@link org.apache.tinkerpop.gremlin.structure.Graph } implementation does not require a
     * {@link org.apache.commons.configuration.Configuration} (or perhaps has a default configuration) it can choose to implement a zero argument
     * open() method. This is an optional constructor method for TinkerGraph. It is not enforced by the Gremlin
     * Test Suite.
     */
    public static TinkerGraph open() {
        return open(EMPTY_CONFIGURATION);
    }

    /**
     * Open a new {@link TinkerGraph} instance.
     * <p/>
     * <b>Reference Implementation Help:</b> This method is the one use by the
     * {@link org.apache.tinkerpop.gremlin.structure.util.GraphFactory} to instantiate
     * {@link org.apache.tinkerpop.gremlin.structure.Graph} instances.  This method must be overridden for the Structure Test
     * Suite to pass. Implementers have latitude in terms of how exceptions are handled within this method.  Such
     * exceptions will be considered implementation specific by the test suite as all test generate graph instances
     * by way of {@link org.apache.tinkerpop.gremlin.structure.util.GraphFactory}. As such, the exceptions get generalized
     * behind that facade and since {@link org.apache.tinkerpop.gremlin.structure.util.GraphFactory} is the preferred method
     * to opening graphs it will be consistent at that level.
     *
     * @param configuration the configuration for the instance
     * @return a newly opened {@link org.apache.tinkerpop.gremlin.structure.Graph}
     */
    public static TinkerGraph open(final Configuration configuration) {
        return new TinkerGraph(configuration);
    }

    ////////////// STRUCTURE API METHODS //////////////////

    @Override
    public Vertex addVertex(final Object... keyValues) {
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        Object idValue = ElementHelper.getIdValue(keyValues).orElse(null);
        final String label = ElementHelper.getLabelValue(keyValues).orElse(Vertex.DEFAULT_LABEL);

        if (null != idValue) {
            if (this.vertices.containsKey(idValue))
                throw Exceptions.vertexWithIdAlreadyExists(idValue);
        } else {
            idValue = vertexIdManager.getNextId(this);
        }

        final Vertex vertex = new TinkerVertex(idValue, label, this);
        this.vertices.put(vertex.id(), vertex);

        ElementHelper.attachProperties(vertex, VertexProperty.Cardinality.list, keyValues);
        return vertex;
    }

    @Override
    public <C extends GraphComputer> C compute(final Class<C> graphComputerClass) {
        if (!graphComputerClass.equals(TinkerGraphComputer.class))
            throw Graph.Exceptions.graphDoesNotSupportProvidedGraphComputer(graphComputerClass);
        return (C) new TinkerGraphComputer(this);
    }

    @Override
    public GraphComputer compute() {
        return new TinkerGraphComputer(this);
    }

    @Override
    public Variables variables() {
        if (null == this.variables)
            this.variables = new TinkerGraphVariables();
        return this.variables;
    }

    @Override
    public String toString() {
        return StringFactory.graphString(this, "vertices:" + this.vertices.size() + " edges:" + this.edges.size());
    }

    public void clear() {
        this.vertices.clear();
        this.edges.clear();
        this.variables = null;
        this.currentId = 0l;
        this.vertexIndex = null;
        this.edgeIndex = null;
    }

    @Override
    public void close() {
        this.graphView = null;
    }

    @Override
    public Transaction tx() {
        throw Exceptions.transactionsNotSupported();
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public Iterator<Vertex> vertices(final Object... vertexIds) {
        // todo: this code looks a lot like edges() code - better reuse here somewhere?
        // todo: what if we have Reference/DetachedVertex???????????????????????????
        if (0 == vertexIds.length) {
            return this.vertices.values().iterator();
        } else if (1 == vertexIds.length) {
            if (vertexIds[0] instanceof Vertex) {
                // no need to get the vertex again, so just flip it back - some implementation may want to treat this
                // as a refresh operation. that's not necessary for tinkergraph.
                return IteratorUtils.of((Vertex) vertexIds[0]);
            } else {
                // convert the id to the expected data type and lookup the vertex
                final Vertex vertex = this.vertices.get(vertexIdManager.convert(vertexIds[0]));
                return null == vertex ? Collections.emptyIterator() : IteratorUtils.of(vertex);
            }
        } else {
            // base the conversion function on the first item in the id list as the expectation is that these
            // id values will be a uniform list
            if (vertexIds[0] instanceof Vertex) {
                // based on the first item assume all vertices in the argument list
                if (!Stream.of(vertexIds).allMatch(id -> id instanceof Vertex))
                    throw Graph.Exceptions.idArgsMustBeEitherIdOrElement();

                // no need to get the vertices again, so just flip it back - some implementation may want to treat this
                // as a refresh operation. that's not necessary for tinkergraph.
                return Stream.of(vertexIds).map(id -> (Vertex) id).iterator();
            } else {
                final Class<?> firstClass = vertexIds[0].getClass();
                if (!Stream.of(vertexIds).map(Object::getClass).allMatch(firstClass::equals))
                    throw Graph.Exceptions.idArgsMustBeEitherIdOrElement();     // todo: change exception to be ids of the same type
                return Stream.of(vertexIds).map(vertexIdManager::convert).map(this.vertices::get).filter(Objects::nonNull).iterator();
            }
        }
    }

    @Override
    public Iterator<Edge> edges(final Object... edgeIds) {
        if (0 == edgeIds.length) {
            return this.edges.values().iterator();
        } else if (1 == edgeIds.length) {
            if (edgeIds[0] instanceof Edge) {
                // no need to get the edge again, so just flip it back - some implementation may want to treat this
                // as a refresh operation. that's not necessary for tinkergraph.
                return IteratorUtils.of((Edge) edgeIds[0]);
            } else {
                // convert the id to the expected data type and lookup the vertex
                final Edge edge = this.edges.get(edgeIdManager.convert(edgeIds[0]));
                return null == edge ? Collections.emptyIterator() : IteratorUtils.of(edge);
            }
        } else {
            // base the conversion function on the first item in the id list as the expectation is that these
            // id values will be a uniform list
            if (edgeIds[0] instanceof Edge) {
                // based on the first item assume all vertices in the argument list
                if (!Stream.of(edgeIds).allMatch(id -> id instanceof Edge))
                    throw Graph.Exceptions.idArgsMustBeEitherIdOrElement();

                // no need to get the vertices again, so just flip it back - some implementation may want to treat this
                // as a refresh operation. that's not necessary for tinkergraph.
                return Stream.of(edgeIds).map(id -> (Edge) id).iterator();
            } else {
                final Class<?> firstClass = edgeIds[0].getClass();
                if (!Stream.of(edgeIds).map(Object::getClass).allMatch(firstClass::equals))
                    throw Graph.Exceptions.idArgsMustBeEitherIdOrElement();     // todo: change exception to be ids of the same type
                return Stream.of(edgeIds).map(edgeIdManager::convert).map(this.edges::get).filter(Objects::nonNull).iterator();
            }
        }
    }

    /**
     * Return TinkerGraph feature set.
     * <p/>
     * <b>Reference Implementation Help:</b> Implementers only need to implement features for which there are
     * negative or instance configured features.  By default, all {@link Features} return true.
     */
    @Override
    public Features features() {
        return TinkerGraphFeatures.INSTANCE;
    }

    public static class TinkerGraphFeatures implements Features {

        static final TinkerGraphFeatures INSTANCE = new TinkerGraphFeatures();

        private TinkerGraphFeatures() {
        }

        @Override
        public GraphFeatures graph() {
            return TinkerGraphGraphFeatures.INSTANCE;
        }

        @Override
        public EdgeFeatures edge() {
            return TinkerGraphEdgeFeatures.INSTANCE;
        }

        @Override
        public VertexFeatures vertex() {
            return TinkerGraphVertexFeatures.INSTANCE;
        }

        @Override
        public String toString() {
            return StringFactory.featureString(this);
        }

    }
    public static class TinkerGraphVertexFeatures implements Features.VertexFeatures {

        static final TinkerGraphVertexFeatures INSTANCE = new TinkerGraphVertexFeatures();
        private TinkerGraphVertexFeatures() {
        }

        @Override
        public boolean supportsCustomIds() {
            return false;
        }

    }
    public static class TinkerGraphEdgeFeatures implements Features.EdgeFeatures {

        static final TinkerGraphEdgeFeatures INSTANCE = new TinkerGraphEdgeFeatures();
        private TinkerGraphEdgeFeatures() {
        }

        @Override
        public boolean supportsCustomIds() {
            return false;
        }

    }
    public static class TinkerGraphGraphFeatures implements Features.GraphFeatures {

        static final TinkerGraphGraphFeatures INSTANCE = new TinkerGraphGraphFeatures();
        private TinkerGraphGraphFeatures() {
        }

        @Override
        public boolean supportsTransactions() {
            return false;
        }

        @Override
        public boolean supportsPersistence() {
            return false;
        }

        @Override
        public boolean supportsThreadedTransactions() {
            return false;
        }

    }

    ///////////// GRAPH SPECIFIC INDEXING METHODS ///////////////

    /**
     * Create an index for said element class ({@link Vertex} or {@link Edge}) and said property key.
     * Whenever an element has the specified key mutated, the index is updated.
     * When the index is created, all existing elements are indexed to ensure that they are captured by the index.
     *
     * @param key          the property key to index
     * @param elementClass the element class to index
     * @param <E>          The type of the element class
     */
    public <E extends Element> void createIndex(final String key, final Class<E> elementClass) {
        if (Vertex.class.isAssignableFrom(elementClass)) {
            if (null == this.vertexIndex) this.vertexIndex = new TinkerIndex<>(this, TinkerVertex.class);
            this.vertexIndex.createKeyIndex(key);
        } else if (Edge.class.isAssignableFrom(elementClass)) {
            if (null == this.edgeIndex) this.edgeIndex = new TinkerIndex<>(this, TinkerEdge.class);
            this.edgeIndex.createKeyIndex(key);
        } else {
            throw new IllegalArgumentException("Class is not indexable: " + elementClass);
        }
    }

    /**
     * Drop the index for the specified element class ({@link Vertex} or {@link Edge}) and key.
     *
     * @param key          the property key to stop indexing
     * @param elementClass the element class of the index to drop
     * @param <E>          The type of the element class
     */
    public <E extends Element> void dropIndex(final String key, final Class<E> elementClass) {
        if (Vertex.class.isAssignableFrom(elementClass)) {
            if (null != this.vertexIndex) this.vertexIndex.dropKeyIndex(key);
        } else if (Edge.class.isAssignableFrom(elementClass)) {
            if (null != this.edgeIndex) this.edgeIndex.dropKeyIndex(key);
        } else {
            throw new IllegalArgumentException("Class is not indexable: " + elementClass);
        }
    }

    /**
     * Return all the keys currently being index for said element class  ({@link Vertex} or {@link Edge}).
     *
     * @param elementClass the element class to get the indexed keys for
     * @param <E>          The type of the element class
     * @return the set of keys currently being indexed
     */
    public <E extends Element> Set<String> getIndexedKeys(final Class<E> elementClass) {
        if (Vertex.class.isAssignableFrom(elementClass)) {
            return null == this.vertexIndex ? Collections.emptySet() : this.vertexIndex.getIndexedKeys();
        } else if (Edge.class.isAssignableFrom(elementClass)) {
            return null == this.edgeIndex ? Collections.emptySet() : this.edgeIndex.getIndexedKeys();
        } else {
            throw new IllegalArgumentException("Class is not indexable: " + elementClass);
        }
    }

    ///////////// HELPERS METHODS ///////////////

    /**
     * Function to coerce a provided identifier to a different type given the expected id type of an element.
     * This allows something like {@code g.V(1,2,3)} and {@code g.V(1l,2l,3l)} to both mean the same thing.
     */
    private UnaryOperator<Object> convertToId(final Object id, final Class<?> elementIdClass) {
        if (id instanceof Number) {
            if (elementIdClass != null) {
                if (elementIdClass.equals(Long.class)) {
                    return o -> ((Number) o).longValue();
                } else if (elementIdClass.equals(Integer.class)) {
                    return o -> ((Number) o).intValue();
                } else if (elementIdClass.equals(Double.class)) {
                    return o -> ((Number) o).doubleValue();
                } else if (elementIdClass.equals(Float.class)) {
                    return o -> ((Number) o).floatValue();
                } else if (elementIdClass.equals(String.class)) {
                    return o -> o.toString();
                }
            }
        } else if (id instanceof String) {
            if (elementIdClass != null) {
                final String s = (String) id;
                if (elementIdClass.equals(Long.class)) {
                    return o -> Long.parseLong(s);
                } else if (elementIdClass.equals(Integer.class)) {
                    return o -> Integer.parseInt(s);
                } else if (elementIdClass.equals(Double.class)) {
                    return o -> Double.parseDouble(s);
                } else if (elementIdClass.equals(Float.class)) {
                    return o -> Float.parseFloat(s);
                } else if (elementIdClass.equals(UUID.class)) {
                    return o -> UUID.fromString(s);
                }
            }
        }

        return UnaryOperator.identity();
    }

    public interface IdManager<T> {
        T getNextId(final TinkerGraph graph);
        Class<? extends T> getIdClass();
        T convert(final Object o);
    }

    public enum DefaultIdManager implements IdManager {
        LONG {
            private long currentId = -1l;
            @Override
            public Long getNextId(final TinkerGraph graph) {
                return Stream.generate(() -> (++currentId)).filter(id -> !graph.vertices.containsKey(id) && !graph.edges.containsKey(id)).findAny().get();
            }

            @Override
            public Class<? extends Long> getIdClass() {
                return Long.class;
            }

            @Override
            public Object convert(final Object o) {
                if (o instanceof Number)
                    return ((Number) o).longValue();
                else if (o instanceof String)
                    return Long.parseLong((String) o);
                else
                    throw new IllegalArgumentException("Expected an id that is convertible to Long");
            }
        },
        INTEGER {
            private int currentId = -1;
            @Override
            public Integer getNextId(final TinkerGraph graph) {
                return Stream.generate(() -> (++currentId)).filter(id -> !graph.vertices.containsKey(id) && !graph.edges.containsKey(id)).findAny().get();
            }

            @Override
            public Class<? extends Integer> getIdClass() {
                return Integer.class;
            }

            @Override
            public Object convert(final Object o) {
                if (o instanceof Number)
                    return ((Number) o).intValue();
                else if (o instanceof String)
                    return Integer.parseInt((String) o);
                else
                    throw new IllegalArgumentException("Expected an id that is convertible to Integer");
            }
        },
        UUID {
            @Override
            public UUID getNextId(final TinkerGraph graph) {
                return java.util.UUID.randomUUID();
            }

            @Override
            public Class<? extends UUID> getIdClass() {
                return java.util.UUID.class;
            }

            @Override
            public Object convert(final Object o) {
                if (o instanceof java.util.UUID)
                    return o;
                else if (o instanceof String)
                    return java.util.UUID.fromString((String) o);
                else
                    throw new IllegalArgumentException("Expected an id that is convertible to UUID");
            }
        },
        ANY {
            private long currentId = -1l;
            @Override
            public Long getNextId(final TinkerGraph graph) {
                return Stream.generate(() -> (++currentId)).filter(id -> !graph.vertices.containsKey(id) && !graph.edges.containsKey(id)).findAny().get();
            }

            @Override
            public Class<? extends Object> getIdClass() {
                return Object.class;
            }

            @Override
            public Object convert(final Object o) {
                return o;
            }
        }
    }
}
