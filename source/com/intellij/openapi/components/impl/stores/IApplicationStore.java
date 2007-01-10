package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.impl.ApplicationImpl;

import java.io.IOException;

public interface IApplicationStore extends IComponentStore {
  void loadApplication(String path);

  void saveApplication(String path) throws IOException;

  void setApplication(ApplicationImpl application);
}
