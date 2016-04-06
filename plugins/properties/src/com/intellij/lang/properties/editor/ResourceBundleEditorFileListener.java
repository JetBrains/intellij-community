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
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Dmitry Batkovich
 */
class ResourceBundleEditorFileListener extends VirtualFileAdapter {
  private final Project myProject;
  private ResourceBundleEditor myEditor;

  public ResourceBundleEditorFileListener(ResourceBundleEditor editor, Project project) {
    myEditor = editor;
    myProject = project;
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    if (PropertiesImplUtil.isPropertiesFile(file, myProject)) {
      asyncUpdateUiIfNeed(() -> {
        if (file.isValid()) {
          for (PropertiesFile f : myEditor.getResourceBundle().getPropertiesFiles()) {
            if (Comparing.equal(f.getVirtualFile(), file)) {
              return f;
            }
          }
        }
        return null;
      }, (f) -> myEditor.recreateEditorsPanel());
    }
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    final VirtualFile eventFile = event.getFile();
    for (PropertiesFile file : myEditor.getTranslationEditors().keySet()) {
      if (Comparing.equal(file.getVirtualFile(), eventFile)) {
        myEditor.recreateEditorsPanel();
        return;
      }
    }
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    final VirtualFile eventFile = event.getFile();
    if (PropertiesImplUtil.isPropertiesFile(eventFile, myProject)) {
      asyncUpdateUiIfNeed(() -> {
        for (Map.Entry<PropertiesFile, EditorEx> e : myEditor.getTranslationEditors().entrySet()) {
          if (eventFile.equals(e.getKey().getVirtualFile())) {
            return e.getValue();
          }
        }
        return null;
      }, (editor) -> {
        if (editor != null) {
          final String propertyName = event.getPropertyName();
          if (VirtualFile.PROP_WRITABLE.equals(propertyName)) {
            editor.setViewer((Boolean)event.getNewValue());
            return;
          }
          if (VirtualFile.PROP_NAME.equals(propertyName)) {
            myEditor.recreateEditorsPanel();
          }
          else {
            myEditor.updateEditorsFromProperties(true);
          }
        }
      });
    }
  }

  private <T> void asyncUpdateUiIfNeed(Supplier<T> uiUpdateCondition, Consumer<T> uiUpdate) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final T obj = uiUpdateCondition.get();
      if (obj != null) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (myEditor.isValid()) {
            uiUpdate.accept(obj);
          }
        });
      }
    });
  }
}
