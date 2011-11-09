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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
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
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(false);
    final Project project = e.getData(LangDataKeys.PROJECT);
    if (project != null) {
      Pair<Sdk, Module> sdkAndModule = findPythonSdkAndModule(project);
      if (sdkAndModule.first != null) {
        e.getPresentation().setEnabled(true);
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

    Pair<Sdk, Module> sdkAndModule = findPythonSdkAndModule(project);

    Module module = sdkAndModule.second;
    Sdk sdk = sdkAndModule.first;

    String[] setup_fragment;

    Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(module);

    String self_path_append = constructPythonPathCommand(pythonPath);

    String customStartScript = PyConsoleOptionsProvider.getInstance(project).getPythonConsoleSettings().getCustomStartScript();

    if (customStartScript.trim().length() > 0) {
      self_path_append += "\n" + customStartScript.trim();
    }

    String workingDir = PyConsoleOptionsProvider.getInstance(project).getPythonConsoleSettings().getWorkingDirectory();
    if (StringUtil.isEmpty(workingDir)) {
      if (module != null) {
        workingDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
      }
      else {
        if (ModuleManager.getInstance(project).getModules().length > 0) {
          workingDir = ModuleRootManager.getInstance(ModuleManager.getInstance(project).getModules()[0]).getContentRoots()[0].getPath();
        }
      }
    }

    BuildoutFacet facet = null;
    if (module != null) {
      facet = BuildoutFacet.getInstance(module);
    }
    if (facet != null) {
      setup_fragment = new String[]{facet.getPathPrependStatement(), self_path_append};
    }
    else {
      setup_fragment = new String[]{self_path_append};
    }

    return PydevConsoleRunner.createAndRun(project, sdk, PyConsoleType.PYTHON, workingDir, setup_fragment);
  }

  @NotNull
  private static Pair<Sdk, Module> findPythonSdkAndModule(Project project) {
    Sdk sdk = null;
    Module module = null;
    PyConsoleOptionsProvider.PyConsoleSettings settings = PyConsoleOptionsProvider.getInstance(project).getPythonConsoleSettings();
    String sdkHome = settings.getSdkHome();
    if (sdkHome != null) {
      sdk = PythonSdkType.findSdkByPath(sdkHome);
      if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      else {
        if (ModuleManager.getInstance(project).getModules().length == 1) {
          module = ModuleManager.getInstance(project).getModules()[0];
        }
        else {
          module = null; //why??? oh no....
        }
      }
    }
    if (sdk == null) {
      for (Module m : ModuleManager.getInstance(project).getModules()) {
        if (PythonSdkType.findPythonSdk(module) != null) {
          sdk = PythonSdkType.findPythonSdk(module);
          module = m;
          break;
        }
      }
    }
    if (sdk == null) {
      if (PythonSdkType.getAllSdks().size() > 0) {
        //noinspection UnusedAssignment
        sdk = PythonSdkType.getAllSdks().get(0); //take any python sdk
      }
    }
    return Pair.create(sdk, module);
  }

  public static String constructPythonPathCommand(Collection<String> pythonPath) {
    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return "'" + FileUtil.toSystemDependentName(input).replace("\\", "\\\\") + "'";
      }
    }));

    return "sys.path.extend([" + path + "])";
  }
}
