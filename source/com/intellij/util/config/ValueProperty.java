package com.intellij.util.config;

/**
 * @author dyoma
 */
public class ValueProperty<T> extends AbstractProperty<T> {
  private final T myDefault;
  private final String myName;

  public ValueProperty(String name, T defaultValue) {
    myName = name;
    myDefault = defaultValue;
  }

  public T copy(T value) {
    return value;
  }

  public T getDefault(AbstractProperty.AbstractPropertyContainer container) {
    return myDefault;
  }

  public String getName() {
    return myName;
  }
}
