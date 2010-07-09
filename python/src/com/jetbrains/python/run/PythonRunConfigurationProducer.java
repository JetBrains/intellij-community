package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonRunConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiFile mySourceFile = null;

  public PythonRunConfigurationProducer() {
    super(PythonConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return mySourceFile;
  }

  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    PsiFile script = location.getPsiElement().getContainingFile();
    if (script == null || script.getFileType() != PythonFileType.INSTANCE) {
      return null;
    }
    Module module = ModuleUtil.findModuleForPsiElement(script);
    if (module != null) {
      for (RunnableScriptFilter f : Extensions.getExtensions(RunnableScriptFilter.EP_NAME)) {
        if (f.isRunnableScript(script, module)) {
          return null;
        }
      }
    }
    mySourceFile = script;

    final Project project = mySourceFile.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    PythonRunConfiguration configuration = (PythonRunConfiguration) settings.getConfiguration();
    final VirtualFile vFile = mySourceFile.getVirtualFile();
    if (vFile == null) return null;
    configuration.setScriptName(vFile.getPath());
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    configuration.setName(configuration.suggestedName());
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    return settings;
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    PsiFile script = location.getPsiElement().getContainingFile();
    if (script == null) {
      return null;
    }
    final VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) {
      return null;
    }
    String path = vFile.getPath();
    for (RunnerAndConfigurationSettings configuration : existingConfigurations) {
      final String scriptName = ((PythonRunConfiguration)configuration.getConfiguration()).getScriptName();
      if (FileUtil.toSystemIndependentName(scriptName).equals(path)) {
        return configuration;
      }
    }
    return null;
  }

  public int compareTo(final Object o) {
    return PREFERED;
  }
}
