package com.intellij.internal.diGraph.impl;

import com.intellij.internal.diGraph.Node;
import com.intellij.internal.diGraph.Edge;
import com.intellij.internal.diGraph.analyzer.MarkedNode;
import com.intellij.internal.diGraph.analyzer.Mark;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.06.2003
 * Time: 23:35:24
 * To change this template use Options | File Templates.
 */
public class NodeImpl implements MarkedNode {
  LinkedList myIn;
  LinkedList myOut;

  public NodeImpl() {
    myIn = new LinkedList();
    myOut = new LinkedList();
  }

  public NodeImpl(EdgeImpl[] in, EdgeImpl[] out) {
    myIn = new LinkedList();
    myOut = new LinkedList();

    for (int i = 0; i < (in == null ? 0 : in.length); i++) {
      myIn.add(in[i]);
      in[i].myEnd = this;
    }

    for (int i = 0; i < (out == null ? 0 : out.length); i++) {
      myOut.add(out[i]);
      out[i].myBeg = this;
    }
  }

  public NodeImpl(LinkedList in, LinkedList out) {
    myIn = in == null ? new LinkedList() : in;
    myOut = out == null ? new LinkedList() : out;

    for (Iterator i = myIn.iterator(); i.hasNext();) ((EdgeImpl) i.next()).myEnd = this;
    for (Iterator i = myOut.iterator(); i.hasNext();) ((EdgeImpl) i.next()).myBeg = this;
  }

  public Iterator inIterator() {
    return myIn.iterator();
  }

  public Iterator outIterator() {
    return myOut.iterator();
  }

  public int inDeg() {
    return myIn.size();
  }

  public int outDeg() {
    return myOut.size();
  }

  public Mark getMark() {
    return null;
  }

  public void setMark(Mark x) {

  }
}
