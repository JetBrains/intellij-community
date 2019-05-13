// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.rest.RestFileType;
import org.jetbrains.annotations.NotNull;

public class RestSplitEditorProvider implements FileEditorProvider, DumbAware {
  public RestSplitEditorProvider() {
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file.getFileType() == RestFileType.INSTANCE;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    TextEditor editor = (TextEditor)TextEditorProvider.getInstance().createEditor(project, file);
    return new TextEditorWithPreview(editor, new RestPreviewFileEditor(file, project));
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "restructured-text-editor";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
