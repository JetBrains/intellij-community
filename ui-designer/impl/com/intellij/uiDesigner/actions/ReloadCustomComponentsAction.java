/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.10.2006
 * Time: 13:35:16
 */
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Ref;
import com.intellij.uiDesigner.LoaderFactory;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.designSurface.GuiEditor;

public class ReloadCustomComponentsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return;
    LoaderFactory.getInstance(project).clearClassLoaderCache();
    final FileEditor[] fileEditors = FileEditorManager.getInstance(project).getAllEditors();
    for(FileEditor editor: fileEditors) {
      if (editor instanceof UIFormEditor) {
        ((UIFormEditor) editor).getEditor().readFromFile(true);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());
    e.getPresentation().setVisible(editor != null && haveCustomComponents(editor));
  }

  private static boolean haveCustomComponents(final GuiEditor editor) {
    // quick & dirty check
    final Ref<Boolean> result = new Ref<Boolean>();
    FormEditingUtil.iterate(editor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        if (!component.getComponentClassName().startsWith("javax.swing")) {
          result.set(Boolean.TRUE);
          return false;
        }
        return true;
      }
    });
    return !result.isNull();
  }
}