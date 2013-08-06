package com.jetbrains.python.module;

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.SupportForFrameworksStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.jetbrains.python.PythonModuleTypeBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonModuleType extends PythonModuleTypeBase<PythonModuleBuilderBase> {
  @NotNull
  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull final WizardContext wizardContext,
                                              @NotNull final PythonModuleBuilderBase moduleBuilder,
                                              @NotNull final ModulesProvider modulesProvider) {
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    final Project project = getProject(wizardContext);
    steps.add(new PythonSdkSelectStep(moduleBuilder, "reference.project.structure.sdk.python", project));
    final List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getProviders(getInstance(), DefaultFacetsProvider.INSTANCE);
    if (!providers.isEmpty()) {
      steps.add(new SupportForFrameworksStep(wizardContext, moduleBuilder, LibrariesContainerFactory.createContainer(project)));
    }
    return steps.toArray(new ModuleWizardStep[steps.size()]);
  }

  private static Project getProject(final WizardContext context) {
    Project project = context.getProject();
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    return project;
  }

  @NotNull
  public PythonModuleBuilder createModuleBuilder() {
    return new PythonModuleBuilder();
  }
}
