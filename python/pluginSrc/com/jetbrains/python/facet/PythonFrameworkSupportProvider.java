package com.jetbrains.python.facet;

import com.intellij.facet.FacetManager;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportConfigurable;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.module.PythonModuleType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFrameworkSupportProvider extends FrameworkSupportProvider {
  public PythonFrameworkSupportProvider() {
    super("Python", PythonFacetType.getInstance().getPresentableName());
  }

  @NotNull
  @Override
  public FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new PythonFrameworkSupportConfigurable(model);
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return !(moduleType instanceof PythonModuleType);
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetsByType(PythonFacetType.getInstance().getId()).size() > 0;
  }
}
