/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.10.2006
 * Time: 13:35:16
 */
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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

public class ReloadCustomComponentsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
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
  public void update(AnActionEvent e) {
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