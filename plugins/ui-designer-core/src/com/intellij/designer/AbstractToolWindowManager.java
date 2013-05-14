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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
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
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractToolWindowManager implements ProjectComponent {
  public static final String EDITOR_MODE = "UI_DESIGNER_EDITOR_MODE.";

  private final MergingUpdateQueue myWindowQueue = new MergingUpdateQueue(getComponentName(), 200, true, null);
  protected final Project myProject;
  protected final FileEditorManager myFileEditorManager;
  protected volatile ToolWindow myToolWindow;
  private volatile boolean myToolWindowReady;
  private volatile boolean myToolWindowDisposed;

  protected final PropertiesComponent myPropertiesComponent;

  private MessageBusConnection myConnection;
  private final FileEditorManagerListener myListener = new FileEditorManagerListener() {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      bindToDesigner(getActiveDesigner());
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          bindToDesigner(getActiveDesigner());
        }
      });
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      bindToDesigner(getDesigner(event.getNewEditor()));
    }
  };

  private final int myStyle;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ToolWindow
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  protected AbstractToolWindowManager(Project project, FileEditorManager fileEditorManager, int style) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    myPropertiesComponent = PropertiesComponent.getInstance(myProject);
    myStyle = style;
  }

  @Override
  public void projectOpened() {
    initToolWindow();

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myToolWindowReady = true;
        if (!isEditorMode()) {
          initListeners();
          bindToDesigner(getActiveDesigner());
        }
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

  private void initListeners() {
    myConnection = myProject.getMessageBus().connect(myProject);
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myListener);
  }

  private void removeListeners() {
    myConnection.disconnect();
    myConnection = null;
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
  public DesignerEditorPanel getActiveDesigner() {
    for (FileEditor editor : myFileEditorManager.getSelectedEditors()) {
      DesignerEditorPanel designer = getDesigner(editor);
      if (designer != null) {
        return designer;
      }
    }

    return null;
  }

  @Nullable
  protected static DesignerCustomizations getCustomizations() {
    return DesignerCustomizations.EP_NAME.findExtension(DesignerCustomizations.class);
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

  protected final void initGearActions() {
    if (!ApplicationManager.getApplication().isInternal()) {
      return; // XXX: delete after test
    }

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ToggleAction("In Editor Mode", "Pin/unpin tool window to Designer Editor", null) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return false;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setEditorMode(true);
      }
    });
    ((ToolWindowEx)myToolWindow).setAdditionalGearActions(group);
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // LightToolWindow
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public final void bind(DesignerEditorPanel designer) {
    if (isEditorMode()) {
      updateContent(designer, true);
    }
  }

  public final void dispose(DesignerEditorPanel designer) {
    if (isEditorMode()) {
      disposeContent(designer);
    }
  }

  protected final Object getContent(DesignerEditorPanel designer) {
    LightToolWindow toolWindow = (LightToolWindow)designer.getClientProperty(getComponentName());
    return toolWindow.getContent();
  }

  protected final void updateContent(DesignerEditorPanel designer, boolean bindToEditor) {
    if (bindToEditor) {
      designer.putClientProperty(getComponentName(), createContent(designer));
    }
    else {
      disposeContent(designer);
    }
  }

  protected abstract LightToolWindow createContent(DesignerEditorPanel designer);

  protected final LightToolWindow createContent(DesignerEditorPanel designer,
                                                LightToolWindowContent content,
                                                String title,
                                                String title2, Icon icon,
                                                JComponent component,
                                                JComponent focusedComponent,
                                                int defaultWidth,
                                                AnAction[] actions) {
    return new LightToolWindow(content,
                               title,
                               title2,
                               icon,
                               component,
                               focusedComponent,
                               designer.getContentSplitter(),
                               myStyle,
                               this,
                               myProject,
                               myPropertiesComponent,
                               getComponentName(),
                               defaultWidth,
                               actions);
  }

  protected final void disposeContent(DesignerEditorPanel designer) {
    String key = getComponentName();
    LightToolWindow toolWindow = (LightToolWindow)designer.getClientProperty(key);
    designer.putClientProperty(key, null);
    toolWindow.dispose();
  }

  private void updateContent(boolean bindToEditor) {
    for (FileEditor editor : myFileEditorManager.getAllEditors()) {
      DesignerEditorPanel designer = getDesigner(editor);
      if (designer != null) {
        updateContent(designer, bindToEditor);
      }
    }
  }

  public final boolean isEditorMode() {
    return myPropertiesComponent.getBoolean(EDITOR_MODE + getComponentName(), false); // XXX: sets true after test
  }

  public final void setEditorMode(boolean value) {
    if (value) {
      removeListeners();
      updateToolWindow(null);
      updateContent(true);
    }
    else {
      updateContent(false);
      initListeners();
      bindToDesigner(getActiveDesigner());
    }
    myPropertiesComponent.setValue(EDITOR_MODE + getComponentName(), Boolean.toString(value));
  }

  final ToolWindow getToolWindow() {
    return myToolWindow;
  }
}