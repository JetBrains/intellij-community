// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.make;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.uiDesigner.editor.UIFormEditor;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class FormElementNavigatable implements Navigatable {
  private final Project myProject;
  private final VirtualFile myVirtualFile;
  private final @Nullable String myComponentId;

  public FormElementNavigatable(final Project project, final VirtualFile virtualFile, final @Nullable String componentId) {
    myProject = project;
    myVirtualFile = virtualFile;
    myComponentId = componentId;
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (!myVirtualFile.isValid()) return;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, myVirtualFile);
    final List<FileEditor> fileEditors = FileEditorManager.getInstance(myProject).openEditor(descriptor, requestFocus);
    if (myComponentId != null) {
      for(FileEditor editor: fileEditors) {
        if (editor instanceof UIFormEditor) {
          ((UIFormEditor) editor).selectComponentById(myComponentId);
          break;
        }
      }
    }
  }

  @Override
  public boolean canNavigate() {
    return myVirtualFile.isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return myVirtualFile.isValid();
  }
}
