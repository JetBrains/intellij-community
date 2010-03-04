package com.jetbrains.python.console;

import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author oleg
 */
public class RunPythonConsoleAction extends AnAction implements DumbAware {

  public RunPythonConsoleAction() {
    super();
    getTemplatePresentation().setIcon(IconLoader.getIcon("/com/jetbrains/python/python.png"));
  }

  @Override
  public void update(final AnActionEvent e) {
    final Module module = (Module)e.getDataContext().getData(LangDataKeys.MODULE.getName());
    e.getPresentation().setVisible(module != null);

    final Sdk sdk = module != null ? PythonSdkType.findPythonSdk(module) : null;
    e.getPresentation().setEnabled(sdk != null);
  }

  public void actionPerformed(final AnActionEvent e) {
    final Module module = (Module)e.getDataContext().getData(LangDataKeys.MODULE.getName());
    assert module != null : "Module cannot be null here";
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    assert sdk != null : "sdk is null";

    PyConsoleRunner.run(module.getProject(),
                        PyBundle.message("python.console"),
                        new CommandLineArgumentsProvider() {
                          public String[] getArguments() {
                            return new String[]{sdk.getHomePath(), "-i"};
                          }

                          public boolean passParentEnvs() {
                            return false;
                          }

                          public Map<String, String> getAdditionalEnvs() {
                            return Collections.emptyMap();
                          }
                        },
                        getFirstContentRoot(module).getPath());
  }

  private static VirtualFile getFirstContentRoot(@NotNull final Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    return roots.length > 0 ? roots[0] : null;
  }
}
