package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public class QuickChangeKeymapAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, DefaultActionGroup group) {
    final KeymapManagerEx manager = (KeymapManagerEx) KeymapManager.getInstance();
    final Keymap[] keymaps = manager.getAllKeymaps();
    final Keymap current = manager.getActiveKeymap();
    for (int i = 0; i < keymaps.length; i++) {
      final Keymap keymap = keymaps[i];
      group.add(new AnAction(keymap.getPresentableName(), "", keymap == current ? ourCurrentAction : ourNotCurrentAction) {
        public void actionPerformed(AnActionEvent e) {
          manager.setActiveKeymap(keymap);
        }
      });
    }
  }

  protected boolean isEnabled() {
    return ((KeymapManagerEx) KeymapManager.getInstance()).getAllKeymaps().length > 1;
  }
}
