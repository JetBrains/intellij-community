package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

public class StructureViewSelectInTarget implements SelectInTarget {
  private Project myProject;

  public StructureViewSelectInTarget(Project project) {
    myProject = project;
  }


  public String toString() {
    return "File Structure";
  }

  public boolean canSelect(SelectInContext context) {
    return context.getStructureViewBuilder() != null;
  }

  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final Object selector = context.getSelectorInFile();
    if (selector == null) return;

    final StructureView structureView = getStructureView();
    ToolWindowManager windowManager=ToolWindowManager.getInstance(context.getProject());
    final Runnable runnable = new Runnable() {
      public void run() {
        VirtualFile virtualFile = context.getVirtualFile();
        FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(virtualFile);
        if (editor != null) {
          structureView.selectCurrentElement(editor,requestFocus);
        }
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }

  }

  private StructureView getStructureView() {
    return StructureViewFactory.getInstance(myProject).getStructureView();
  }

  public String getToolWindowId() {
    return ToolWindowId.STRUCTURE_VIEW;
  }

  public String getMinorViewId() {
    return null;
  }
}
