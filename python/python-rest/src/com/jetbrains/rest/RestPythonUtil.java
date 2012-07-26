package com.jetbrains.rest;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * User : catherine
 */
public class RestPythonUtil {
  private RestPythonUtil() {}

  public static Presentation updateDocutilRequiredAction(final AnActionEvent e) {
    return updateRequirements(e, "rst2html.py");
  }

  public static Presentation updateRequirements(final AnActionEvent e, String runner) {
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    presentation.setEnabled(false);
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
          String htmlRunner = RestUtil.findRunner(sdk.getHomePath(), runner);
          if (htmlRunner != null) {
            presentation.setVisible(true);
            presentation.setEnabled(true);
          }
        }
      }
    }
    return presentation;
  }

  public static Presentation updateSphinxBuildRequiredAction(final AnActionEvent e) {
    return updateRequirements(e, "sphinx-build"+ (SystemInfo.isWindows ? ".exe" : ""));
  }

  public static Presentation updateSphinxQuickStartRequiredAction(final AnActionEvent e) {
    return updateRequirements(e, "sphinx-quickstart"+ (SystemInfo.isWindows ? ".exe" : ""));
  }

}
