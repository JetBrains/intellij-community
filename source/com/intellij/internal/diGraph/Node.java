package com.intellij.internal.diGraph;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.06.2003
 * Time: 19:39:49
 * To change this template use Options | File Templates.
 */
public interface Node<EDGE_TYPE extends Edge> {
  Iterator<EDGE_TYPE> inIterator();
  Iterator<EDGE_TYPE> outIterator();

  int inDeg();
  int outDeg();
}
