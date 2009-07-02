package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;

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

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
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
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    PythonRunConfiguration configuration = (PythonRunConfiguration) settings.getConfiguration();
    configuration.setScriptName(mySourceFile.getVirtualFile().getPath());
    configuration.setName(configuration.suggestedName());
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    copyStepsBeforeRun(project, configuration);
    return settings;
  }

  public int compareTo(final Object o) {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
