/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class ResourceBundleEditorProvider extends FileTypeFactory implements FileEditorProvider, DumbAware {
  private static final ResourceBundleFileType RESOURCE_BUNDLE_FILE_TYPE = new ResourceBundleFileType();

  @Override
  public boolean accept(@NotNull final Project project, @NotNull final VirtualFile file){
    if (file instanceof ResourceBundleAsVirtualFile) return true;
    if (!file.isValid()) return false;
    final FileType type = file.getFileType();
    if (type != StdFileTypes.PROPERTIES && type != StdFileTypes.XML) return false;

    return ReadAction.compute(() -> {
      if (project.isDisposed()) return Boolean.FALSE;
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
      return propertiesFile != null &&  propertiesFile.getResourceBundle().getPropertiesFiles().size() > 1;
    });
  }

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull final VirtualFile file){
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

    return new ResourceBundleEditor(resourceBundle);
  }

  @Override
  @NotNull
  public FileEditorState readState(@NotNull Element element, @NotNull Project project, @NotNull VirtualFile file) {
    return new ResourceBundleEditor.ResourceBundleEditorState(null);
  }

  @Override
  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }

  @Override
  @NotNull
  public String getEditorTypeId(){
    return "ResourceBundle";
  }


  @Override
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    consumer.consume(RESOURCE_BUNDLE_FILE_TYPE, "");
  }
}
