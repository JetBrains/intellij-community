/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.graph;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;

import java.util.*;

/**
 *  @author dsl, ven
 */
public class DFSTBuilder<Node> {
  private final Graph<Node> myGraph;
  private final Map<Node, Integer> myNodeToNNumber;
  private Map<Node, Integer> myNodeToTNumber;
  private final Node[] myInvN;
  private Pair<Node,Node> myBackEdge = null;

  private Comparator<Node> myComparator = null;
  private boolean myNBuilt = false;
  private boolean myTBuilt = false;

  public DFSTBuilder(Graph<Node> graph) {
    myGraph = graph;
    myNodeToNNumber = new HashMap<Node, Integer>(myGraph.getNodes().size());
    myInvN = (Node[])new Object[myGraph.getNodes().size()];
  }

  public void buildDFST() {
    if (myNBuilt) return;
    Collection<Node> nodes = myGraph.getNodes();
    int indexN = nodes.size();
    HashSet<Node> processed = new HashSet<Node>();
    for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
      Node node = iterator.next();
      if (!myGraph.getIn(node).hasNext()) {
        indexN = traverseSubGraph(node, indexN, processed);
      }
    }

    for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
      Node node = iterator.next();
      indexN = traverseSubGraph(node, indexN, processed);
    }

    myNBuilt = true;
  }

  public Comparator<Node> comparator() {
    if (myComparator == null) {
      buildDFST();
      if (isAcyclic()) {
        myComparator = new Comparator<Node>() {
          public int compare(Node t, Node t1) {
            return myNodeToNNumber.get(t).compareTo(myNodeToNNumber.get(t1));
          }
        };
      }
      else {
        build_T();
        myComparator = new Comparator<Node>() {
          public int compare(Node t, Node t1) {
            return myNodeToTNumber.get(t).compareTo(myNodeToTNumber.get(t1));
          }
        };
      }
    }
    return myComparator;
  }

  private int traverseSubGraph (final Node node, int nNumber, Set<Node> processed) {
    if (!processed.contains(node)) {
      processed.add(node);
      for (Iterator<Node> it = myGraph.getOut(node); it.hasNext(); ) {
        nNumber = traverseSubGraph(it.next(), nNumber, processed);
      }

      nNumber--;
      myNodeToNNumber.put(node, new Integer(nNumber));
      myInvN[nNumber] = node;

      //Check for cycles
      if (myBackEdge == null) {
        for (Iterator<Node> it = myGraph.getIn(node); it.hasNext(); ) {
          Node prev = it.next();
          Integer prevNumber = myNodeToNNumber.get(prev);
          if (prevNumber != null && prevNumber.intValue() > nNumber) {
            myBackEdge = new Pair<Node, Node> (node, prev);
            break;
          }
        }
      }
    }

    return nNumber;
  }

  private Set<Node> region (Node v) {
    LinkedList<Node> frontier = new LinkedList<Node>();
    frontier.addFirst(v);
    Set<Node> result = new HashSet<Node>();
    int number = myNodeToNNumber.get(v).intValue();
    while (!frontier.isEmpty()) {
      Node curr = frontier.removeFirst();
      result.add(curr);
      Iterator<Node> it = myGraph.getIn(curr);
      while (it.hasNext()) {
        Node w = it.next();
        if (myNodeToNNumber.get(w).intValue() > number && !result.contains(w)) frontier.add(w);
      }
    }

    return result;
  }

  private void build_T() {
    if (myTBuilt) return;
    int currT = 0;
    int size = myGraph.getNodes().size();
    myNodeToTNumber = new HashMap<Node, Integer>(size);
    for (int i = 0; i < size; i++) {
      Node v = myInvN[i];
      if (!myNodeToTNumber.containsKey(v)) {
        myNodeToTNumber.put(v, new Integer(currT++));
        Set<Node> region = region(v);
        for (Iterator<Node> iterator = region.iterator(); iterator.hasNext();) {
          Node w = iterator.next();
          if (w != v) {
            myNodeToTNumber.put(w, new Integer(currT++));
          }
        }
      }
    }
    myTBuilt = true;
  }

  public Pair<Node, Node> getCircularDependency() {
    buildDFST();
    return myBackEdge;
  }

  public boolean isAcyclic () {
    return getCircularDependency() == null;
  }
}
