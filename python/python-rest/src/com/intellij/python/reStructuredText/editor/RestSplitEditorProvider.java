// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.editor;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.reStructuredText.RestFileType;
import org.jetbrains.annotations.NotNull;

final class RestSplitEditorProvider implements FileEditorProvider, DumbAware {
  RestSplitEditorProvider() {
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, RestFileType.INSTANCE);
  }

  @Override
  public boolean acceptRequiresReadAction() {
    return false;
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
