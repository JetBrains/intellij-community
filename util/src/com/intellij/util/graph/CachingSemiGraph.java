/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.graph;

import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 *  @author dsl
 */
public class CachingSemiGraph<Node> implements GraphGenerator.SemiGraph<Node> {
  private final Set<Node> myNodes;
  private final Map<Node, Set<Node>> myIn;

  public CachingSemiGraph(GraphGenerator.SemiGraph<Node> original) {
    myIn = new HashMap<Node, Set<Node>>();
    myNodes = new HashSet<Node>();
    for (Iterator<Node> it = original.getNodes().iterator(); it.hasNext();) {
      myNodes.add(it.next());
    }
    for (Iterator<Node> it = myNodes.iterator(); it.hasNext(); ) {
      final Node node = it.next();
      final Set<Node> value = new HashSet<Node>();
      for(Iterator<Node> itin = original.getIn(node); itin.hasNext(); ) {
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
