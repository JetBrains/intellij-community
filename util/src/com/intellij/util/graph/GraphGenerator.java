/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.graph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Collection;

/**
 *  @author dsl
 */
public class GraphGenerator<Node> implements Graph <Node>{
  private final SemiGraph<Node> myGraph;

  public interface SemiGraph<Node> {
    Collection<Node> getNodes();
    Iterator<Node> getIn(Node n);
  }

  private final com.intellij.util.containers.HashMap<Node, Set<Node>> myOuts;

  public GraphGenerator(SemiGraph<Node> graph) {
    myGraph = graph;
    myOuts = new com.intellij.util.containers.HashMap<Node, Set<Node>>();
    buildOuts();
  }

  public static <T> GraphGenerator<T> create(SemiGraph<T> graph) {
    return new GraphGenerator<T>(graph);
  }

  private void buildOuts() {
    Collection<Node> nodes = myGraph.getNodes();
    for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
      Node node = iterator.next();
      myOuts.put(node, new HashSet<Node>());
    }

    for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
      Node node = iterator.next();
      Iterator<Node> inIt = myGraph.getIn(node);
      while (inIt.hasNext()) {
        Node inNode = inIt.next();
        myOuts.get(inNode).add(node);
      }
    }
  }

  public Collection<Node> getNodes() {
    return myGraph.getNodes();
  }

  public Iterator<Node> getIn(Node n) {
    return myGraph.getIn(n);
  }

  public Iterator<Node> getOut(Node n) {
    return myOuts.get(n).iterator();
  }
}
