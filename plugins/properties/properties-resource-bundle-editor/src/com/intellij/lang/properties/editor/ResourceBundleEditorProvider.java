// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class ResourceBundleEditorProvider implements FileEditorProvider, DumbAware {
  @Override
  public boolean accept(final @NotNull Project project, final @NotNull VirtualFile file){
    if (file instanceof ResourceBundleAsVirtualFile) {
      return true;
    }
    if (!file.isValid()) {
      return false;
    }
    FileType type = file.getFileType();
    if (type != PropertiesFileType.INSTANCE && type != StdFileTypes.XML) {
      return false;
    }

    return ReadAction.compute(() -> {
      if (project.isDisposed()) return Boolean.FALSE;
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
      return propertiesFile != null &&  propertiesFile.getResourceBundle().getPropertiesFiles().size() > 1;
    });
  }

  @Override
  public boolean acceptRequiresReadAction() {
    return false;
  }

  @Override
  public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    ResourceBundle resourceBundle;
    if (file instanceof ResourceBundleAsVirtualFile) {
      resourceBundle = ((ResourceBundleAsVirtualFile)file).getResourceBundle();
    }
    else {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) {
        throw new IllegalArgumentException("psifile cannot be null");
      }
      resourceBundle = PropertiesImplUtil.getPropertiesFile(psiFile).getResourceBundle();
    }

    return new ResourceBundleEditor(project, file, resourceBundle);
  }

  @Override
  public @NotNull FileEditorState readState(@NotNull Element element, @NotNull Project project, @NotNull VirtualFile file) {
    return new ResourceBundleEditor.ResourceBundleEditorState(null);
  }

  @Override
  public @NotNull FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }

  @Override
  public @NotNull String getEditorTypeId(){
    return "ResourceBundle";
  }
}
