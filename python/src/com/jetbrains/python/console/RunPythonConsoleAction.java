package com.jetbrains.python.console;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author oleg
 */
public class RunPythonConsoleAction extends AnAction implements DumbAware {

  public RunPythonConsoleAction() {
    super();
    getTemplatePresentation().setIcon(IconLoader.getIcon("/com/jetbrains/python/icons/python.png"));
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(false);
    e.getPresentation().setEnabled(false);
    final Project project = e.getData(LangDataKeys.PROJECT);
    if (project != null) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        e.getPresentation().setVisible(true);
        if (PythonSdkType.findPythonSdk(module) != null) {
          e.getPresentation().setEnabled(true);
          break;
        }
      }
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(LangDataKeys.PROJECT);
    runPythonConsole(project);
  }

  @Nullable
  public static PydevConsoleRunner runPythonConsole(Project project) {
    assert project != null : "Project is null";
    Sdk sdk = null;
    Module module = null;
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      module = m;
      sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null) {
        break;
      }
    }
    assert module != null : "Module is null";
    assert sdk != null : "Sdk is null";

    String[] setup_fragment;

    Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(module);

    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return "'" + input + "'";
      }
    }));
    String workingDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
    final String self_path_append = "sys.path.extend([" + path + "])";
    BuildoutFacet facet = BuildoutFacet.getInstance(module);
    if (facet != null) {
      setup_fragment = new String[]{facet.getPathPrependStatement(), self_path_append};
    }
    else {
      setup_fragment = new String[]{self_path_append};
    }

    return PydevConsoleRunner.run(project, sdk, PyBundle.message("python.console"), workingDir, setup_fragment);
  }
}
