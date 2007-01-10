package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.BaseComponent;

public interface IComponentStore {
  void initStore();
  void initComponent(BaseComponent component, Class componentClass);

  void dispose();
}
