package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;

interface Binding {
  Object serialize(Object o, Object context);

  @Nullable
  Object deserialize(Object context, Object... nodes);

  boolean isBoundTo(Object node);

  Class getBoundNodeType();

  void init();
}
