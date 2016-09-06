'''
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
'''

__author__ = 'Marko A. Rodriguez (http://markorodriguez.com)'

import json
import unittest
from unittest import TestCase

from gremlin_python.structure.graph import Vertex
from gremlin_python.structure.io.graphson import GraphSONReader
from gremlin_python.structure.io.graphson import GraphSONWriter


class TestGraphSONReader(TestCase):
    def test_numbers(self):
        x = GraphSONReader.readObject(json.dumps({
            "@type": "g:Int32",
            "@value": 31
        }))
        assert isinstance(x, int)
        assert 31 == x
        ##
        x = GraphSONReader.readObject(json.dumps({
            "@type": "g:Int64",
            "@value": 31
        }))
        assert isinstance(x, long)
        assert 31L == x
        ##
        x = GraphSONReader.readObject(json.dumps({
            "@type": "g:Float",
            "@value": 31.3
        }))
        assert isinstance(x, float)
        assert 31.3 == x
        ##
        x = GraphSONReader.readObject(json.dumps({
            "@type": "g:Double",
            "@value": 31.2
        }))
        assert isinstance(x, float)
        assert 31.2 == x

    def test_graph(self):
        vertex = GraphSONReader.readObject(
            """{"@type":"g:Vertex", "@value":{"id":{"@type":"g:Int32","@value":1},"label":"person","outE":{"created":[{"id":{"@type":"g:Int32","@value":9},"inV":{"@type":"g:Int32","@value":3},"properties":{"weight":{"@type":"g:Double","@value":0.4}}}],"knows":[{"id":{"@type":"g:Int32","@value":7},"inV":{"@type":"g:Int32","@value":2},"properties":{"weight":{"@type":"g:Double","@value":0.5}}},{"id":{"@type":"g:Int32","@value":8},"inV":{"@type":"g:Int32","@value":4},"properties":{"weight":{"@type":"g:Double","@value":1.0}}}]},"properties":{"name":[{"id":{"@type":"g:Int64","@value":0},"value":"marko"}],"age":[{"id":{"@type":"g:Int64","@value":1},"value":{"@type":"g:Int32","@value":29}}]}}}""")
        assert isinstance(vertex, Vertex)
        assert "person" == vertex.label
        assert 1 == vertex.id
        assert isinstance(vertex.id, int)
        assert vertex == Vertex(1)

class TestGraphSONWriter(TestCase):
    def test_numbers(self):
        assert """{"@type":"g:Int32","@value":1}""" == GraphSONWriter.writeObject(1)
        assert """{"@type":"g:Int64","@value":2}""" == GraphSONWriter.writeObject(2L)
        assert """{"@type":"g:Float","@value":3.2}""" == GraphSONWriter.writeObject(3.2)
        assert """true""" == GraphSONWriter.writeObject(True)


if __name__ == '__main__':
    unittest.main()
