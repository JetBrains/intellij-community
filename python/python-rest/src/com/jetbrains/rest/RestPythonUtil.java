package com.jetbrains.rest;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * User : catherine
 */
public class RestPythonUtil {
  private RestPythonUtil() {}

  public static Presentation updateSphinxQuickStartRequiredAction(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      Module module = e.getData(LangDataKeys.MODULE);
      if (module == null) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        module = modules.length == 0 ? null : modules [0];
      }
      if (module != null) {
        Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          PyPackageManagerImpl manager = (PyPackageManagerImpl)PyPackageManager.getInstance(sdk);
          try {
            final PyPackage sphinx = manager.findPackage("Sphinx");
            String quickStart = RestUtil.findQuickStart(sdk.getHomePath());
            presentation.setEnabled(sphinx != null && quickStart != null);
          }
          catch (PyExternalProcessException ignored) {
          }
        }
      }
    }
    return presentation;
  }

}
