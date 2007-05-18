package org.jetbrains.idea.maven.core.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleCircularDependencyException;

import java.awt.event.InputEvent;
import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public class IdeaAPIHelper {

  public static void executeAction(final String actionId, final InputEvent e) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(actionId);
    if (action != null) {
      final Presentation presentation = new Presentation();
      final AnActionEvent event = new AnActionEvent(e, DataManager.getInstance().getDataContext(), "", presentation, actionManager, 0);
      action.update(event);
      if (presentation.isEnabled()) {
        action.actionPerformed(event);
      }
    }
  }

  public static void deleteModules(final Collection<Module> modules) {
    if (modules.isEmpty()) {
      return;
    }
    final ModifiableModuleModel model = ModuleManager.getInstance(modules.iterator().next().getProject()).getModifiableModel();
    for (Module module : modules) {
      model.disposeModule(module);
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          model.commit();
        }
        catch (ModuleCircularDependencyException ignore) {
        }
      }
    });
  }
}
