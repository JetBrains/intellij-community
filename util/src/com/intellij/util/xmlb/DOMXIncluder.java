package com.intellij.util.xmlb;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;


class XIncludeNodeList implements NodeList {

  private List<Node> data = new ArrayList<Node>();

  // could easily expose more List methods if they seem useful
  public void add(int index, Node node) {
    data.add(index, node);
  }

  public void add(Node node) {
    data.add(node);
  }

  public void add(NodeList nodes) {
    for (int i = 0; i < nodes.getLength(); i++) {
      data.add(nodes.item(i));
    }
  }

  public Node item(int index) {
    return data.get(index);
  }

  // copy DOM JavaDoc
  public int getLength() {
    return data.size();
  }

}


class XIncludeException extends RuntimeException {

  public XIncludeException() {
  }

  public XIncludeException(final String message) {
    super(message);
  }

  public XIncludeException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public XIncludeException(final Throwable cause) {
    super(cause);
  }
}