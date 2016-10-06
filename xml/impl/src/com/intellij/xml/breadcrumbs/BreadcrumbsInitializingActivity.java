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
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BreadcrumbsInitializingActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    if (project.isDefault()) {
      return;
    }

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener(project));

    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(project), project);
    connection.subscribe(UISettingsListener.TOPIC, new MyUISettingsListener(project));
  }

  private static class MyFileEditorManagerListener extends FileEditorManagerAdapter {
    @Override
    public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
      reinitBreadcrumbsComponent(source, file);
    }
  }

  private static class MyVirtualFileListener extends VirtualFileAdapter {
    private final Project myProject;

    public MyVirtualFileListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName()) && !myProject.isDisposed()) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
        VirtualFile file = event.getFile();
        if (fileEditorManager.isFileOpen(file)) {
          reinitBreadcrumbsComponent(fileEditorManager, file);
        }
      }
    }
  }

  private static class MyFileTypeListener implements FileTypeListener {
    private final Project myProject;

    public MyFileTypeListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void fileTypesChanged(@NotNull FileTypeEvent event) {
      if (!myProject.isDisposed()) {
        reinitBreadcrumbsInAllEditors(myProject);
      }
    }
  }

  private static class MyUISettingsListener implements UISettingsListener {
    private final Project myProject;

    public MyUISettingsListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void uiSettingsChanged(UISettings uiSettings) {
      if (!myProject.isDisposed()) {
        reinitBreadcrumbsInAllEditors(myProject);
      }
    }
  }

  private static void reinitBreadcrumbsInAllEditors(@NotNull Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
      reinitBreadcrumbsComponent(fileEditorManager, virtualFile);
    }
  }

  private static void reinitBreadcrumbsComponent(@NotNull final FileEditorManager fileEditorManager, @NotNull VirtualFile file) {
    if (isSuitable(fileEditorManager.getProject(), file)) {
      FileEditor[] fileEditors = fileEditorManager.getAllEditors(file);
      for (final FileEditor fileEditor : fileEditors) {
        if (fileEditor instanceof TextEditor) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          if (BreadcrumbsXmlWrapper.getBreadcrumbsComponent(editor) != null) {
            continue;
          }
          final BreadcrumbsXmlWrapper wrapper = new BreadcrumbsXmlWrapper(editor);
          final JComponent c = wrapper.getComponent();
          fileEditorManager.addTopComponent(fileEditor, c);

          Disposer.register(fileEditor, new Disposable() {
            @Override
            public void dispose() {
              disposeWrapper(fileEditorManager, fileEditor, wrapper);
            }
          });
        }
      }
    }
    else {
      removeBreadcrumbs(fileEditorManager, file);
    }
  }

  private static void removeBreadcrumbs(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile file) {
    final FileEditor[] fileEditors = fileEditorManager.getAllEditors(file);
    for (FileEditor fileEditor : fileEditors) {
      if (fileEditor instanceof TextEditor) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        BreadcrumbsXmlWrapper wrapper = BreadcrumbsXmlWrapper.getBreadcrumbsComponent(editor);
        if (wrapper != null) {
          disposeWrapper(fileEditorManager, fileEditor, wrapper);
        }
      }
    }
  }

  private static boolean isSuitable(@NotNull Project project, @NotNull VirtualFile file) {
    if (file instanceof HttpVirtualFile) {
      return false;
    }

    final FileViewProvider provider = PsiManager.getInstance(project).findViewProvider(file);
    return provider != null && BreadcrumbsXmlWrapper.findInfoProvider(provider) != null;
  }

  private static void disposeWrapper(@NotNull FileEditorManager fileEditorManager,
                                     @NotNull FileEditor fileEditor,
                                     @NotNull BreadcrumbsXmlWrapper wrapper) {
    fileEditorManager.removeTopComponent(fileEditor, wrapper.getComponent());
    Disposer.dispose(wrapper);
  }
}