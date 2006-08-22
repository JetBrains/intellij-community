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

import com.intellij.openapi.util.Pair;
import gnu.trove.TIntArrayList;

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
  private TIntArrayList mySCCs = null;
  private Node[] myInvT;

  public DFSTBuilder(Graph<Node> graph) {
    myGraph = graph;
    myNodeToNNumber = new LinkedHashMap<Node, Integer>(myGraph.getNodes().size());
    myInvN = (Node[])new Object[myGraph.getNodes().size()];
  }

  public void buildDFST() {
    if (myNBuilt) return;
    Collection<Node> nodes = myGraph.getNodes();
    int indexN = nodes.size();
    Set<Node> processed = new LinkedHashSet<Node>();
    for (Node node : nodes) {
      if (!myGraph.getIn(node).hasNext()) {
        indexN = traverseSubGraph(node, indexN, processed);
      }
    }

    for (Node node : nodes) {
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
    Set<Node> result = new LinkedHashSet<Node>();
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

    myInvT = (Node[])new Object[myGraph.getNodes().size()];
    mySCCs = new TIntArrayList ();

    int currT = 0;
    int size = myGraph.getNodes().size();

    myNodeToTNumber = new LinkedHashMap<Node, Integer>(size);

    for (int i = 0; i < size; i++) {
      Node v = myInvN[i];
      if (!myNodeToTNumber.containsKey(v)) {
        final Set<Node> region = region(v);

        mySCCs.add(region.size());

        myNodeToTNumber.put(v, new Integer(currT));
        myInvT[currT++]=v;

        for (Node w : region) {
          if (w != v) {
            myNodeToTNumber.put(w, new Integer(currT));
            myInvT[currT++] = w;
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

  public Node getNodeByNNumber (final int n){
    return myInvN[n];
  }

  public Node getNodeByTNumber (final int n){
      return myInvT[n];
    }

  /**
   *
   * @return the list containing the number of nodes in strongly connected components.
   * Respective nodes could be obtained via {@link #getNodeByTNumber(int)}. 
   */
  public TIntArrayList getSCCs (){
    if (!myNBuilt){
      buildDFST();
    }

    if (!myTBuilt){
      build_T();
    }

    return mySCCs;
  }
}
