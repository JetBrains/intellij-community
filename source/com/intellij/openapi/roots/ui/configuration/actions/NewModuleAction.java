package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 5, 2004
 */
public class NewModuleAction extends AnAction {
  public NewModuleAction() {
    super(ProjectBundle.message("module.new.action"), ProjectBundle.message("module.new.action.description"), null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = getProject(e);
    if (project == null) {
      return;
    }
    Object dataFromContext = prepareDataFromContext(e);
    final AddModuleWizard wizard = new AddModuleWizard(project, new DefaultModulesProvider(project), null);

    wizard.show();

    if (wizard.isOK()) {
      final ProjectBuilder builder = wizard.getProjectBuilder();
      if (builder instanceof ModuleBuilder) {
        final ModuleBuilder moduleBuilder = (ModuleBuilder)builder;
        if (moduleBuilder.getName() == null) {
          moduleBuilder.setName(wizard.getProjectName());
        }
        if (moduleBuilder.getModuleFilePath() == null) {
          moduleBuilder.setModuleFilePath(wizard.getModuleFilePath());
        }
      }
      if (!builder.validate(project, project)) {
        return;
      }
      if (builder instanceof ModuleBuilder) {
        Module module = ((ModuleBuilder) builder).commitModule(project);
        processCreatedModule(module, dataFromContext);
      }
      else {
        builder.commit(project);
      }
    }
  }

  @Nullable
  protected Object prepareDataFromContext(final AnActionEvent e) {
    return null;
  }

  protected void processCreatedModule(final Module module, final Object dataFromContext) {
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getProject(e) != null);
  }

  @Nullable
  private static Project getProject(AnActionEvent e) {
    return e.getData(PlatformDataKeys.PROJECT);
  }

}
