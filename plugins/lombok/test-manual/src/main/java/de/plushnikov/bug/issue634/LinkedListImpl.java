package de.plushnikov.bug.issue634;

import lombok.Builder;

@Builder
public class LinkedListImpl {

  private Node first;
  private Node.NodeBuilder last;

  public void add(Node o1) {
    Node node = new Node(o1, last);
  }

}
