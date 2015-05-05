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
package org.apache.tinkerpop.gremlin.process.traversal;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.PartitionStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.finalization.ProfileStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.optimization.RangeByIsCountStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.verification.LambdaRestrictionStrategy;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * A {@link TraversalStrategy} defines a particular atomic operation for mutating a {@link Traversal} prior to its evaluation.
 * Traversal strategies are typically used for optimizing a traversal for the particular underlying graph engine.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface TraversalStrategy<S extends TraversalStrategy> extends Serializable, Comparable<Class<? extends TraversalStrategy>> {


    public void apply(final Traversal.Admin<?, ?> traversal);

    public default Set<Class<? extends S>> applyPrior() {
        return Collections.emptySet();
    }

    public default Set<Class<? extends S>> applyPost() {
        return Collections.emptySet();
    }

    public default Class<S> getTraversalCategory() {
        return (Class) TraversalStrategy.class;
    }

    @Override
    public default int compareTo(final Class<? extends TraversalStrategy> otherTraversalCategory) {
        return 0;
    }

    /**
     * Implemented by strategies that adds "application logic" to the traversal (e.g. {@link PartitionStrategy}).
     */
    public interface DecorationStrategy extends TraversalStrategy<DecorationStrategy> {

        @Override
        public default Class<DecorationStrategy> getTraversalCategory() {
            return DecorationStrategy.class;
        }

        @Override
        public default int compareTo(final Class<? extends TraversalStrategy> otherTraversalCategory) {
            if (otherTraversalCategory.equals(DecorationStrategy.class))
                return 0;
            else if (otherTraversalCategory.equals(OptimizationStrategy.class))
                return -1;
            else if (otherTraversalCategory.equals(FinalizationStrategy.class))
                return -1;
            else if (otherTraversalCategory.equals(VerificationStrategy.class))
                return -1;
            else
                return 0;
        }
    }

    /**
     * Implemented by strategies that rewrite the traversal to be more efficient, but with the same semantics
     * (e.g. {@link RangeByIsCountStrategy}).
     */
    public interface OptimizationStrategy extends TraversalStrategy<OptimizationStrategy> {

        @Override
        public default Class<OptimizationStrategy> getTraversalCategory() {
            return OptimizationStrategy.class;
        }

        @Override
        public default int compareTo(final Class<? extends TraversalStrategy> otherTraversalCategory) {
            if (otherTraversalCategory.equals(DecorationStrategy.class))
                return 1;
            else if (otherTraversalCategory.equals(OptimizationStrategy.class))
                return 0;
            else if (otherTraversalCategory.equals(FinalizationStrategy.class))
                return -1;
            else if (otherTraversalCategory.equals(VerificationStrategy.class))
                return -1;
            else
                return 0;
        }
    }

    /**
     * Implemented by strategies that do final behaviors that require a fully compiled traversal to work (e.g.
     * {@link ProfileStrategy}).
     */
    public interface FinalizationStrategy extends TraversalStrategy<FinalizationStrategy> {

        @Override
        public default Class<FinalizationStrategy> getTraversalCategory() {
            return FinalizationStrategy.class;
        }

        @Override
        public default int compareTo(final Class<? extends TraversalStrategy> otherTraversalCategory) {
            if (otherTraversalCategory.equals(DecorationStrategy.class))
                return 1;
            else if (otherTraversalCategory.equals(OptimizationStrategy.class))
                return 1;
            else if (otherTraversalCategory.equals(FinalizationStrategy.class))
                return 0;
            else if (otherTraversalCategory.equals(VerificationStrategy.class))
                return -1;
            else
                return 0;
        }
    }
    /**
     * Implemented by strategies where there is no more behavioral tweaking of the traversal required.  Strategies that
     * implement this marker will simply analyze the traversal and throw exceptions if the traversal is not correct
     * for the execution  (e.g. {@link LambdaRestrictionStrategy}).
     */
    public interface VerificationStrategy extends TraversalStrategy<VerificationStrategy> {

        @Override
        public default Class<VerificationStrategy> getTraversalCategory() {
            return VerificationStrategy.class;
        }

        @Override
        public default int compareTo(final Class<? extends TraversalStrategy> otherTraversalCategory) {
            if (otherTraversalCategory.equals(DecorationStrategy.class))
                return 1;
            else if (otherTraversalCategory.equals(OptimizationStrategy.class))
                return 1;
            else if (otherTraversalCategory.equals(FinalizationStrategy.class))
                return 1;
            else
                return 0;
        }
    }
}
