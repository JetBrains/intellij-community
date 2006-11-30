package com.intellij.util.xmlb;

import org.w3c.dom.Node;

interface Binding {
    Node serialize(Object o, Node context);
    Object deserialize(Object context, Node node);
    boolean isBoundTo(Node node);
    Class<? extends Node> getBoundNodeType();
}
