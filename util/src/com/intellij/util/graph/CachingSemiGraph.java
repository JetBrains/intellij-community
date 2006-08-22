/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.graph;

import java.util.*;

/**
 *  @author dsl
 */
public class CachingSemiGraph<Node> implements GraphGenerator.SemiGraph<Node> {
  private final Set<Node> myNodes;
  private final Map<Node, Set<Node>> myIn;

  public CachingSemiGraph(GraphGenerator.SemiGraph<Node> original) {
    myIn = new LinkedHashMap<Node, Set<Node>>();
    myNodes = new LinkedHashSet<Node>();
    for (final Node node1 : original.getNodes()) {
      myNodes.add(node1);
    }
    for (final Node node : myNodes) {
      final Set<Node> value = new LinkedHashSet<Node>();
      for (Iterator<Node> itin = original.getIn(node); itin.hasNext();) {
        value.add(itin.next());
      }
      myIn.put(node, value);
    }
  }

  public static <T> CachingSemiGraph<T> create(GraphGenerator.SemiGraph<T> original) {
    return new CachingSemiGraph<T>(original);
  }

  public Collection<Node> getNodes() {
    return myNodes;
  }

  public Iterator<Node> getIn(Node n) {
    return myIn.get(n).iterator();
  }
}
