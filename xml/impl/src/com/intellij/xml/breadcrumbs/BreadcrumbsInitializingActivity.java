/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class BreadcrumbsInitializingActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    if (project.isDefault() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        reinitBreadcrumbsInAllEditors(project);
      }
    });

    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(project), project);
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> reinitBreadcrumbsInAllEditors(project));
  }

  private static class MyFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
      reinitBreadcrumbsComponent(source, file);
    }
  }

  private static class MyVirtualFileListener implements VirtualFileListener {
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

  private static void reinitBreadcrumbsInAllEditors(@NotNull Project project) {
    if (project.isDisposed()) return;
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
      reinitBreadcrumbsComponent(fileEditorManager, virtualFile);
    }
  }

  private static void reinitBreadcrumbsComponent(@NotNull final FileEditorManager fileEditorManager, @NotNull VirtualFile file) {
    if (isSuitable(fileEditorManager.getProject(), file)) {
      FileEditor[] fileEditors = fileEditorManager.getAllEditors(file);
      for (final FileEditor fileEditor : fileEditors) {
        if (fileEditor instanceof TextEditor && fileEditor.isValid()) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          final BreadcrumbsXmlWrapper existingWrapper = BreadcrumbsXmlWrapper.getBreadcrumbsComponent(editor);
          if (existingWrapper != null) {
            existingWrapper.queueUpdate();
            continue;
          }

          final BreadcrumbsXmlWrapper wrapper = new BreadcrumbsXmlWrapper(editor);
          registerWrapper(fileEditorManager, fileEditor, wrapper);
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

    return BreadcrumbsXmlWrapper.findInfoProvider(file, project) != null;
  }

  private static void registerWrapper(@NotNull FileEditorManager fileEditorManager,
                                      @NotNull FileEditor fileEditor,
                                      @NotNull BreadcrumbsXmlWrapper wrapper) {
    //noinspection deprecation
    if (wrapper.breadcrumbs.above) {
      fileEditorManager.addTopComponent(fileEditor, wrapper);
    }
    else {
      fileEditorManager.addBottomComponent(fileEditor, wrapper);
    }
    Disposer.register(fileEditor, () -> disposeWrapper(fileEditorManager, fileEditor, wrapper));
  }

  private static void disposeWrapper(@NotNull FileEditorManager fileEditorManager,
                                     @NotNull FileEditor fileEditor,
                                     @NotNull BreadcrumbsXmlWrapper wrapper) {
    //noinspection deprecation
    if (wrapper.breadcrumbs.above) {
      fileEditorManager.removeTopComponent(fileEditor, wrapper);
    }
    else {
      fileEditorManager.removeBottomComponent(fileEditor, wrapper);
    }
    Disposer.dispose(wrapper);
  }
}