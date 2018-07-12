// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class UIFormEditorProvider implements FileEditorProvider, DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.editor.UIFormEditorProvider");

  @Override
  public boolean accept(@NotNull final Project project, @NotNull final VirtualFile file){
    return
      file.getFileType() == StdFileTypes.GUI_DESIGNER_FORM &&
      !StdFileTypes.GUI_DESIGNER_FORM.isBinary() &&
      (ModuleUtilCore.findModuleForFile(file, project) != null || file instanceof LightVirtualFile);
  }

  @Override
  @NotNull public FileEditor createEditor(@NotNull final Project project, @NotNull final VirtualFile file){
    LOG.assertTrue(accept(project, file));
    return new UIFormEditor(project, file);
  }

  @Override
  @NotNull
  public FileEditorState readState(@NotNull Element element, @NotNull final Project project, @NotNull final VirtualFile file){
    //TODO[anton,vova] implement
    return new MyEditorState(-1, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  @NotNull public String getEditorTypeId(){
    return "ui-designer";
  }

  @Override
  @NotNull public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}