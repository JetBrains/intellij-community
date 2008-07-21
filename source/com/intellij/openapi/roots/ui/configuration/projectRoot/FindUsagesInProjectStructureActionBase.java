package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

/**
 * @author nik
 */
public abstract class FindUsagesInProjectStructureActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase");
  private static final Icon FIND_ICON = IconLoader.getIcon("/actions/find.png");
  private final JComponent myParentComponent;
  private Project myProject;

  public FindUsagesInProjectStructureActionBase(JComponent parentComponent, Project project) {
    super(ProjectBundle.message("find.usages.action.text"), ProjectBundle.message("find.usages.action.text"), FIND_ICON);
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(), parentComponent);
    myParentComponent = parentComponent;
    myProject = project;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled());
  }

  protected abstract boolean isEnabled();

  public void actionPerformed(AnActionEvent e) {
    final Object selectedObject = getSelectedObject();
    if (selectedObject == null) return;

    final Set<String> dependencies = getContext().getCachedDependencies(selectedObject, true);
    if (dependencies == null || dependencies.isEmpty()) {
      Messages.showInfoMessage(myParentComponent, FindBundle.message("find.usage.view.no.usages.text"),
                               FindBundle.message("find.pointcut.applications.not.found.title"));
      return;
    }
    RelativePoint point = getPointToShowResults();
    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>(
      ProjectBundle.message("dependencies.used.in.popup.title"), dependencies.toArray(new String[dependencies.size()])) {

      public PopupStep onChosen(final String nameToSelect, final boolean finalChoice) {
        navigateToObject(nameToSelect, selectedObject);
        return FINAL_CHOICE;
      }

      public Icon getIconFor(String selection) {
        final Module module = getContext().myModulesConfigurator.getModule(selection);
        LOG.assertTrue(module != null, selection + " was not found");
        return module.getModuleType().getNodeIcon(false);
      }

    }).show(point);
  }

  private void navigateToObject(final String moduleName, final @NotNull Object selectedObject) {
    ModulesConfigurator modulesConfigurator = getContext().myModulesConfigurator;
    Module module = modulesConfigurator.getModule(moduleName);
    if (module == null) {
      ProjectStructureConfigurable.getInstance(myProject).select(moduleName, null, true);
      return;
    }

    ModuleRootModel rootModel = modulesConfigurator.getRootModel(module);
    OrderEntry entry;
    if (selectedObject instanceof Library) {
      entry = OrderEntryUtil.findLibraryOrderEntry(rootModel, (Library)selectedObject);
    }
    else if (selectedObject instanceof Module) {
      entry = OrderEntryUtil.findModuleOrderEntry(rootModel, (Module)selectedObject);
    }
    else if (selectedObject instanceof Sdk) {
      entry = OrderEntryUtil.findJdkOrderEntry(rootModel, (Sdk)selectedObject);
    }
    else {
      entry = null;
    }
    ModuleStructureConfigurable.getInstance(myProject).selectOrderEntry(module, entry);
  }

  @Nullable
  protected abstract Object getSelectedObject();

  protected StructureConfigurableContext getContext() {
    return ModuleStructureConfigurable.getInstance(myProject).getContext();
  }

  protected abstract RelativePoint getPointToShowResults();
}
