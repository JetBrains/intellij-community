package com.intellij.ide.structureView;

import com.intellij.openapi.fileEditor.FileEditor;

public interface StructureView {
  int SORT_ALPHA = 0;
  int SORT_SOURCE = 1;
  int SORT_ALPHA_VISIBILITY = 2;
  int SORT_VISIBILITY = 3;

  boolean select(Object element, FileEditor fileEditor, boolean requestFocus);
  FileEditor getFileEditor();
}