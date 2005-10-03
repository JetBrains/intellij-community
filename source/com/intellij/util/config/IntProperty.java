package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

public class IntProperty extends ValueProperty<Integer> {
  public IntProperty(@NonNls String name, int defaultValue) {
    super(name, new Integer(defaultValue));
  }

  public int value(AbstractPropertyContainer container) {
    return get(container).intValue();
  }

  public void primSet(AbstractPropertyContainer container, int value) {
    set(container, new Integer(value));
  }
}
