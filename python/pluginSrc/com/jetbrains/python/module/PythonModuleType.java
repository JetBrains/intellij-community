package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class PythonModuleType extends ModuleType<PythonModuleBuilder> {
  @NonNls public static final String PYTHON_MODULE = "PYTHON_MODULE";
  private final Icon myBigIcon = IconLoader.getIcon("/com/jetbrains/python/python_24.png");
  private final Icon myOpenIcon = IconLoader.getIcon("/com/jetbrains/python/pythonOpen.png");
  private final Icon myClosedIcon = IconLoader.getIcon("/com/jetbrains/python/pythonClosed.png");

  public PythonModuleType() {
    super(PYTHON_MODULE);
  }

  public static PythonModuleType getInstance() {
    return (PythonModuleType)ModuleTypeManager.getInstance().findByID(PYTHON_MODULE);
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext,
                                              final PythonModuleBuilder moduleBuilder,
                                              final ModulesProvider modulesProvider) {
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    steps.add(new PythonSdkSelectStep(moduleBuilder, null, null, wizardContext.getProject()));
    return steps.toArray(new ModuleWizardStep[steps.size()]);
  }

  public PythonModuleBuilder createModuleBuilder() {
    return new PythonModuleBuilder();
  }

  public String getName() {
    return "Python Module";
  }

  public String getDescription() {
    return "Provides facilities for developing Python applications";
  }

  public Icon getBigIcon() {
    return myBigIcon;
  }

  public Icon getNodeIcon(final boolean isOpened) {
    return isOpened ? myOpenIcon : myClosedIcon;
  }
}
