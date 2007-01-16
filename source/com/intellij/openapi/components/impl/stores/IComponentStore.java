package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public interface IComponentStore {
  void initStore();
  void initComponent(Object component);

  void dispose();

  List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures);
}
