package com.intellij.openapi.components.impl.stores;

public interface IComponentStore {
  void initStore();
  void initComponent(Object component, Class componentClass);

  void dispose();
}
