package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;

class AccessorBindingWrapper implements Binding {
  private Accessor myAccessor;
  private Binding myBinding;


  public AccessorBindingWrapper(final Accessor accessor, final Binding binding) {
    myAccessor = accessor;
    myBinding = binding;
  }

  public Object serialize(Object o, Object context) {
    return myBinding.serialize(myAccessor.read(o), context);
  }

  @Nullable
  public Object deserialize(Object context, Object... nodes) {
    myAccessor.write(context, myBinding.deserialize(myAccessor.read(context), nodes));
    return context;
  }

  public boolean isBoundTo(Object node) {
    return myBinding.isBoundTo(node);
  }

  public Class getBoundNodeType() {
    return myBinding.getBoundNodeType();
  }

  public void init() {
  }
}
