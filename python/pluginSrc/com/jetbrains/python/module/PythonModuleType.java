package com.jetbrains.python.module;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.SupportForFrameworksStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.jetbrains.python.PythonModuleTypeBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonModuleType extends PythonModuleTypeBase<PythonModuleBuilder> {
  public static PythonModuleType getInstance() {
    return (PythonModuleType)ModuleTypeManager.getInstance().findByID(PYTHON_MODULE);
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext,
                                              final PythonModuleBuilder moduleBuilder,
                                              final ModulesProvider modulesProvider) {
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    steps.add(new PythonSdkSelectStep(moduleBuilder, null, "reference.project.structure.sdk.python", wizardContext.getProject()));
    final List<FrameworkSupportProvider> frameworkSupportProviderList = FrameworkSupportUtil.getProviders(getInstance());
    if (!frameworkSupportProviderList.isEmpty()) {
      steps.add(new SupportForFrameworksStep(moduleBuilder, LibrariesContainerFactory.createContainer(wizardContext.getProject())));
    }
    return steps.toArray(new ModuleWizardStep[steps.size()]);
  }

  public PythonModuleBuilder createModuleBuilder() {
    return new PythonModuleBuilder();
  }
}
