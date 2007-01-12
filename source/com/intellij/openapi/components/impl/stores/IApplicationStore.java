package com.intellij.openapi.components.impl.stores;

import java.io.IOException;

public interface IApplicationStore extends IComponentStore {
  void loadApplication(String path);

  void saveApplication(String path) throws IOException;
}
