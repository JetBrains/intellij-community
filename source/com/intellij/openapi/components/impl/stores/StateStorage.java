package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.List;

interface StateStorage {
  @Nullable
  Element getState(final Object component, final String componentName) throws StateStorageException;

  void setState(final Object component, final String componentName, final Element domElement) throws StateStorageException;

  void save() throws StateStorageException;

  boolean needsSave() throws StateStorageException;

  List<VirtualFile> getAllStorageFiles();

  class StateStorageException extends Exception {
    public StateStorageException() {
    }

    public StateStorageException(final String message) {
      super(message);
    }

    public StateStorageException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public StateStorageException(final Throwable cause) {
      super(cause);
    }
  }
}
