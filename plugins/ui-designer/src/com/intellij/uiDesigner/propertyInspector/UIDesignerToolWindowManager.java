/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class UIDesignerToolWindowManager implements ProjectComponent {
  private final Project myProject;
  private MyToolWindowPanel myToolWindowPanel;
  private ComponentTree myComponentTree;
  private ComponentTreeBuilder myComponentTreeBuilder;
  private PropertyInspector myPropertyInspector;
  private final FileEditorManager myFileEditorManager;
  private ToolWindow myToolWindow;
  private boolean myToolWindowReady = false;
  private boolean myToolWindowDisposed = false;
  private List<TreeSelectionListener> myPendingListeners = new ArrayList<TreeSelectionListener>();

  public UIDesignerToolWindowManager(final Project project, final FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    MyFileEditorManagerListener listener = new MyFileEditorManagerListener();
    myFileEditorManager.addFileEditorManagerListener(listener,project);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myToolWindowReady = true;
      }
    });
  }

  private void checkInitToolWindow() {
    if (myToolWindowReady && !myToolWindowDisposed && myToolWindow == null) {
      initToolWindow();
    }
  }

  private void initToolWindow() {
    myToolWindowPanel = new MyToolWindowPanel();
    myComponentTree = new ComponentTree(myProject);
    for (TreeSelectionListener listener : myPendingListeners) {
      myComponentTree.addTreeSelectionListener(listener);
    }
    myPendingListeners.clear();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myComponentTree);
    scrollPane.setPreferredSize(new Dimension(250, -1));
    myComponentTree.initQuickFixManager(scrollPane.getViewport());
    myPropertyInspector= new PropertyInspector(myProject, myComponentTree);
    myToolWindowPanel.setFirstComponent(scrollPane);
    myToolWindowPanel.setSecondComponent(myPropertyInspector);
    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(UIDesignerBundle.message("toolwindow.ui.designer"),
                                                                               myToolWindowPanel,
                                                                               ToolWindowAnchor.LEFT, myProject, true);
    myToolWindow.setIcon(IconLoader.getIcon("/com/intellij/uiDesigner/icons/toolWindowUIDesigner.png"));
    myToolWindow.setAvailable(false, null);
  }

  public void projectClosed() {
    if (myToolWindowPanel != null) {
      if (myComponentTreeBuilder != null) {
        Disposer.dispose(myComponentTreeBuilder);
      }
      myToolWindowPanel = null;
      myToolWindow = null;
      myToolWindowDisposed = true;
    }
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "UIDesignerToolWindowManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("property.inspector", 200, true, null);

  private void processFileEditorChange(final UIFormEditor newEditor) {
    myQueue.cancelAllUpdates();
    myQueue.queue(new Update("update") {
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) return;
        GuiEditor activeFormEditor = newEditor != null ? newEditor.getEditor() : null;
        if (myToolWindow == null) {
          if (activeFormEditor == null) return;
          initToolWindow();
        }
        if (myComponentTreeBuilder != null) {
          Disposer.dispose(myComponentTreeBuilder);
          myComponentTreeBuilder = null;
        }
        myComponentTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
        myComponentTree.setEditor(activeFormEditor);
        myComponentTree.setFormEditor(newEditor);
        myPropertyInspector.setEditor(activeFormEditor);
        if (activeFormEditor == null) {
          myToolWindow.setAvailable(false, null);
        }
        else {
          myComponentTreeBuilder = new ComponentTreeBuilder(myComponentTree, activeFormEditor);
          myToolWindow.setAvailable(true, null);
          myToolWindow.show(null);
        }
      }
    });
  }

  @Nullable
  public UIFormEditor getActiveFormFileEditor() {
    FileEditor[] fileEditors = myFileEditorManager.getSelectedEditors();
    if (fileEditors.length > 0 && fileEditors [0] instanceof UIFormEditor) {
      return (UIFormEditor) fileEditors [0];
    }
    return null;
  }

  @Nullable
  public GuiEditor getActiveFormEditor() {
    UIFormEditor formEditor = getActiveFormFileEditor();
    return formEditor == null ? null : formEditor.getEditor();
  }

  public static UIDesignerToolWindowManager getInstance(Project project) {
    return project.getComponent(UIDesignerToolWindowManager.class);
  }

  public ComponentTree getComponentTree() {
    checkInitToolWindow();
    return myComponentTree;
  }

  public ComponentTreeBuilder getComponentTreeBuilder() {
    return myComponentTreeBuilder;
  }

  public PropertyInspector getPropertyInspector() {
    return myPropertyInspector;
  }

  public void refreshErrors() {
    myComponentTree.refreshIntentionHint();
    myComponentTree.repaint(myComponentTree.getVisibleRect());

    // PropertyInspector
    myPropertyInspector.refreshIntentionHint();
    myPropertyInspector.repaint(myPropertyInspector.getVisibleRect());
  }

  public void updateComponentTree() {
    myComponentTreeBuilder.updateFromRoot();
  }

  public void addComponentSelectionListener(TreeSelectionListener treeSelectionListener) {
    if (myComponentTree != null) {
      myComponentTree.addTreeSelectionListener(treeSelectionListener);
    }
    else {
      myPendingListeners.add(treeSelectionListener);
    }
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(FileEditorManager source, VirtualFile file) {
      processFileEditorChange(getActiveFormFileEditor());
    }

    public void fileClosed(FileEditorManager source, VirtualFile file) {
      processFileEditorChange(getActiveFormFileEditor());
    }

    public void selectionChanged(FileEditorManagerEvent event) {
      UIFormEditor newEditor = event.getNewEditor() instanceof UIFormEditor ? (UIFormEditor)event.getNewEditor() : null;
      processFileEditorChange(newEditor);
    }
  }

  private class MyToolWindowPanel extends Splitter implements DataProvider {
    MyToolWindowPanel() {
      super(true, 0.33f);
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (GuiEditor.DATA_KEY.is(dataId)) {
        return getActiveFormEditor();
      }
      return null;
    }
  }
}
