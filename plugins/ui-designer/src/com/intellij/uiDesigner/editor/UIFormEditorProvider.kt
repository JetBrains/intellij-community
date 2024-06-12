// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.editor;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SlowOperations;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class UIFormEditorProvider implements FileEditorProvider, DumbAware {
  private static final Logger LOG = Logger.getInstance(UIFormEditorProvider.class);

  @Override
  public boolean accept(final @NotNull Project project, final @NotNull VirtualFile file){
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-762786")) {
      return
        FileTypeRegistry.getInstance().isFileOfType(file, GuiFormFileType.INSTANCE) &&
        !GuiFormFileType.INSTANCE.isBinary() &&
        (ModuleUtilCore.findModuleForFile(file, project) != null || file instanceof LightVirtualFile);
    }
  }

  @Override
  public @NotNull FileEditor createEditor(final @NotNull Project project, final @NotNull VirtualFile file){
    LOG.assertTrue(accept(project, file));
    return new UIFormEditor(project, file);
  }

  @Override
  public @NotNull FileEditorState readState(@NotNull Element element, final @NotNull Project project, final @NotNull VirtualFile file){
    //TODO[anton,vova] implement
    return new MyEditorState(-1, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public @NotNull String getEditorTypeId(){
    return "ui-designer";
  }

  @Override
  public @NotNull FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}