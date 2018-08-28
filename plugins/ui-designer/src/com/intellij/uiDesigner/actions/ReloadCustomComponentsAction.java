// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.LoaderFactory;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.radComponents.RadErrorComponent;
import org.jetbrains.annotations.NotNull;

public class ReloadCustomComponentsAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
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
  public void update(@NotNull AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());
    e.getPresentation().setVisible(editor != null && haveCustomComponents(editor));
  }

  private static boolean haveCustomComponents(final GuiEditor editor) {
    // quick & dirty check
    if (editor.isFormInvalid()) {
      return true;
    }
    final Ref<Boolean> result = new Ref<>();
    FormEditingUtil.iterate(editor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      @Override
      public boolean visit(final IComponent component) {
        if (component instanceof RadErrorComponent || !component.getComponentClassName().startsWith("javax.swing")) {
          result.set(Boolean.TRUE);
          return false;
        }
        return true;
      }
    });
    return !result.isNull();
  }
}