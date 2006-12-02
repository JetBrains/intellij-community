package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Node;

class AccessorBindingWrapper implements Binding {
  private Accessor myAccessor;
  private Binding myBinding;


  public AccessorBindingWrapper(final Accessor accessor, final Binding binding) {
    myAccessor = accessor;
    myBinding = binding;
  }

  public Node serialize(Object o, Node context) {
    return myBinding.serialize(myAccessor.read(o), context);
  }

  @Nullable
  public Object deserialize(Object context, Node... nodes) {
    myAccessor.write(context, myBinding.deserialize(context, nodes));
    return context;
  }

  public boolean isBoundTo(Node node) {
    return myBinding.isBoundTo(node);
  }

  public Class<? extends Node> getBoundNodeType() {
    return myBinding.getBoundNodeType();
  }
}
