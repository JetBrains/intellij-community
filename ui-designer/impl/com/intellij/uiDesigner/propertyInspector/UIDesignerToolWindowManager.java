/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * @author yole
 */
public class UIDesignerToolWindowManager implements ProjectComponent {
  private Project myProject;
  private Splitter myToolWindowPanel;
  private ComponentTree myComponentTree;
  private ComponentTreeBuilder myComponentTreeBuilder;
  private PropertyInspector myPropertyInspector;
  private FileEditorManager myFileEditorManager;
  private MyFileEditorManagerListener myListener;
  private ToolWindow myToolWindow;

  public UIDesignerToolWindowManager(final Project project, final FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    myListener = new MyFileEditorManagerListener();
    myFileEditorManager.addFileEditorManagerListener(myListener);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myToolWindowPanel = new Splitter(true, 0.33f);
        myComponentTree = new ComponentTree();
        final JScrollPane scrollPane = new JScrollPane(myComponentTree);
        scrollPane.setPreferredSize(new Dimension(250, -1));
        myPropertyInspector= new PropertyInspector(myProject, myComponentTree);
        myToolWindowPanel.setFirstComponent(scrollPane);
        myToolWindowPanel.setSecondComponent(myPropertyInspector);
        myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(UIDesignerBundle.message("toolwindow.ui.designer"),
                                                                                   myToolWindowPanel,
                                                                                   ToolWindowAnchor.LEFT);

      }
    });
  }

  public void projectClosed() {
    if (myToolWindowPanel != null) {
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(UIDesignerBundle.message("toolwindow.ui.designer"));
      myFileEditorManager.removeFileEditorManagerListener(myListener);
      myToolWindowPanel = null;
      myToolWindow = null;
    }
  }

  @NonNls
  public String getComponentName() {
    return "UIDesignerToolWindowManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void processFileEditorChange() {
    if (myToolWindow == null) return;
    GuiEditor activeFormEditor = getActiveFormEditor();
    if (myComponentTreeBuilder != null) {
      myComponentTreeBuilder.dispose();
      myComponentTreeBuilder = null;
    }
    myComponentTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myComponentTree.setEditor(activeFormEditor);
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

  @Nullable
  public UIFormEditor getActiveFormFileEditor() {
    GuiEditor activeFormEditor = null;
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

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(FileEditorManager source, VirtualFile file) {
      processFileEditorChange();
    }

    public void fileClosed(FileEditorManager source, VirtualFile file) {
      processFileEditorChange();
    }

    public void selectionChanged(FileEditorManagerEvent event) {
      processFileEditorChange();
    }
  }
}
