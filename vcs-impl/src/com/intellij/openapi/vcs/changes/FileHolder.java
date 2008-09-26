package com.intellij.openapi.vcs.changes;

interface FileHolder {
  void cleanAll();
  void cleanScope(VcsDirtyScope scope);
  FileHolder copy();
  HolderType getType();

  static enum HolderType {
    DELETED,
    UNVERSIONED,
    SWITCHED,
    MODIFIED_WITHOUT_EDITING,
    IGNORED,
    LOCKED
  }
}
