package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public class QuickChangeColorSchemeAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, DefaultActionGroup group) {
    final EditorColorsScheme[] schemes = EditorColorsManager.getInstance().getAllSchemes();
    EditorColorsScheme current = EditorColorsManager.getInstance().getGlobalScheme();
    for (int i = 0; i < schemes.length; i++) {
      final EditorColorsScheme scheme = schemes[i];
      group.add(new AnAction(scheme.getName(), "", scheme == current ? ourCurrentAction : ourNotCurrentAction) {
        public void actionPerformed(AnActionEvent e) {
          EditorColorsManager.getInstance().setGlobalScheme(scheme);
          Editor[] editors = EditorFactory.getInstance().getAllEditors();
          for (int i = 0; i < editors.length; i++) {
            EditorEx editor = (EditorEx) editors[i];
            editor.reinitSettings();
          }
        }
      });
    }
  }

  protected boolean isEnabled() {
    return EditorColorsManager.getInstance().getAllSchemes().length > 1;
  }
}
