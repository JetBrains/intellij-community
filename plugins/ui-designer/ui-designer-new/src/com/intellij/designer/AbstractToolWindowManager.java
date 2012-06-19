/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractToolWindowManager implements ProjectComponent {
  private final MergingUpdateQueue myWindowQueue = new MergingUpdateQueue(getComponentName(), 200, true, null);
  protected final Project myProject;
  protected final FileEditorManager myFileEditorManager;
  protected ToolWindow myToolWindow;
  private boolean myToolWindowReady;
  private boolean myToolWindowDisposed;

  public AbstractToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        bindToDesigner(getActiveDesigner());
      }

      @Override
      public void fileClosed(FileEditorManager source, VirtualFile file) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            bindToDesigner(getActiveDesigner());
          }
        });
      }

      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        bindToDesigner(getDesigner(event.getNewEditor()));
      }
    });
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myToolWindowReady = true;
      }
    });
  }

  @Override
  public void projectClosed() {
    if (!myToolWindowDisposed) {
      disposeComponent();
      myToolWindowDisposed = true;
      myToolWindow = null;
    }
  }

  @Nullable
  private static DesignerEditorPanel getDesigner(FileEditor editor) {
    if (editor instanceof DesignerEditor) {
      DesignerEditor designerEditor = (DesignerEditor)editor;
      return designerEditor.getDesignerPanel();
    }
    return null;
  }

  @Nullable
  public DesignerEditor getActiveDesignerEditor() {
    for (FileEditor editor : myFileEditorManager.getSelectedEditors()) {
      if (editor instanceof DesignerEditor) {
        return (DesignerEditor)editor;
      }
    }
    return null;
  }

  @Nullable
  public DesignerEditorPanel getActiveDesigner() {
    for (FileEditor editor : myFileEditorManager.getSelectedEditors()) {
      DesignerEditorPanel designer = getDesigner(editor);
      if (designer != null) {
        return designer;
      }
    }

    return null;
  }

  private void bindToDesigner(final DesignerEditorPanel designer) {
    myWindowQueue.cancelAllUpdates();
    myWindowQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        if (myToolWindow == null) {
          if (designer == null) {
            return;
          }
          initToolWindow();
        }
        updateToolWindow(designer);
      }
    });
  }

  protected abstract void initToolWindow();

  protected abstract void updateToolWindow(@Nullable DesignerEditorPanel designer);

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }
}