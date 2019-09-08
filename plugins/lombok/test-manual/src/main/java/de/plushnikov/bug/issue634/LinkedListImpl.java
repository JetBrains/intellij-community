package de.plushnikov.bug.issue634;

public class LinkedListImpl {

    private Node first;
    private Node last;
    public void add(Object o) {
        Node node = new Node(o, first);
   }
}
