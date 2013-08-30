package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.jetbrains.python.sdk.PythonSdkUpdater;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyActiveSdkModuleConfigurable extends ModuleAwareProjectConfigurable implements Configurable.Composite {
  private final Project myProject;
  private final Configurable mySdkConfigurable;

  public PyActiveSdkModuleConfigurable(Project project) {
    super(project, "Project Interpreter", "reference.settings.project.interpreter");
    myProject = project;
    mySdkConfigurable = new PythonSdkConfigurable(project);
  }

  @NotNull
  @Override
  protected UnnamedConfigurable createModuleConfigurable(Module module) {
    return new PyActiveSdkConfigurable(module);
  }

  @Override
  protected UnnamedConfigurable createDefaultProjectConfigurable() {
    return new PyActiveSdkConfigurable(myProject);
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();

    // TODO[catherine] proper per-module caching of framework installed state
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null) {
        VFSTestFrameworkListener.getInstance().updateAllTestFrameworks(sdk);
        break;
      }

    }
    PythonSdkUpdater.getInstance().updateActiveSdks(myProject, 0);
  }

  @Override
  public Configurable[] getConfigurables() {
    return new Configurable[] { mySdkConfigurable };
  }
}
