package com.jetbrains.python.console;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.django.facet.DjangoFacet;
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
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      Pair<Sdk, Module> sdkAndModule = findPythonSdkAndModule(project, e.getData(LangDataKeys.MODULE));
      if (sdkAndModule.first != null) {
        e.getPresentation().setEnabled(true);
      }
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    runPythonConsole(project, e.getData(LangDataKeys.MODULE));
  }

  @Nullable
  public static PydevConsoleRunner runPythonConsole(Project project, Module contextModule) {
    assert project != null : "Project is null";

    Pair<Sdk, Module> sdkAndModule = findPythonSdkAndModule(project, contextModule);

    Module module = sdkAndModule.second;
    Sdk sdk = sdkAndModule.first;

    String[] setup_fragment;

    Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(module);

    String self_path_append = constructPythonPathCommand(pythonPath);

    PyConsoleOptionsProvider.PyConsoleSettings settingsProvider = PyConsoleOptionsProvider.getInstance(project).getPythonConsoleSettings();

    String customStartScript = settingsProvider.getCustomStartScript();

    if (customStartScript.trim().length() > 0) {
      self_path_append += "\n" + customStartScript.trim();
    }

    String workingDir = settingsProvider.getWorkingDirectory();
    if (StringUtil.isEmpty(workingDir)) {
      if (module != null && ModuleRootManager.getInstance(module).getContentRoots().length > 0) {
        workingDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
      }
      else {
        if (ModuleManager.getInstance(project).getModules().length > 0) {
          VirtualFile[] roots = ModuleRootManager.getInstance(ModuleManager.getInstance(project).getModules()[0]).getContentRoots();
          if (roots.length > 0) {
            workingDir = roots[0].getPath();
          }
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

    return PydevConsoleRunner.createAndRun(project, sdk, PyConsoleType.PYTHON, workingDir, settingsProvider.getEnvs(), setup_fragment);
  }

  @NotNull
  private static Pair<Sdk, Module> findPythonSdkAndModule(Project project, Module contextModule) {
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
        module = contextModule;
        if (module == null && ModuleManager.getInstance(project).getModules().length > 0) {
          module = ModuleManager.getInstance(project).getModules()[0];
        }
      }
    }
    if (sdk == null && settings.isUseModuleSdk()) {
      if (contextModule != null) {
        module = contextModule;
      }
      else if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      if (module != null) {
        if (PythonSdkType.findPythonSdk(module) != null) {
          sdk = PythonSdkType.findPythonSdk(module);
        }
      }
    }
    else if (contextModule != null) {
      if (module == null) {
        module = contextModule;
      }
      if (sdk == null) {
        sdk = PythonSdkType.findPythonSdk(module);
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
