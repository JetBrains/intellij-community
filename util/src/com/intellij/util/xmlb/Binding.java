package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Node;

interface Binding {
  Node serialize(Object o, Node context);

  @Nullable
  Object deserialize(Object context, Node... nodes);

  boolean isBoundTo(Node node);

  Class<? extends Node> getBoundNodeType();
}
