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
package org.apache.tinkerpop.gremlin.structure.io.gryo;

import org.apache.tinkerpop.gremlin.structure.io.gryo.kryoshim.InputShim;
import org.apache.tinkerpop.gremlin.structure.io.gryo.kryoshim.KryoShim;
import org.apache.tinkerpop.gremlin.structure.io.gryo.kryoshim.OutputShim;
import org.apache.tinkerpop.gremlin.structure.io.gryo.kryoshim.SerializerShim;

import java.net.URI;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
final class URISerializer implements SerializerShim<URI> {

    public URISerializer() { }

    @Override
    public <O extends OutputShim> void write(final KryoShim<?, O> kryo, final O output, final URI uri) {
        output.writeString(uri.toString());
    }

    @Override
    public <I extends InputShim> URI read(final KryoShim<I, ?> kryo, final I input, final Class<URI> uriClass) {
        return URI.create(input.readString());
    }

    @Override
    public boolean isImmutable() {
        return true;
    }
}