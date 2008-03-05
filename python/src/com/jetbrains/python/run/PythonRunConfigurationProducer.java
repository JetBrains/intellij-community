package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
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
    mySourceFile = location.getPsiElement().getContainingFile();
    if (mySourceFile == null || mySourceFile.getFileType() != PythonFileType.INSTANCE) {
      return null;
    }

    final Project project = mySourceFile.getProject();
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    PythonRunConfiguration configuration = (PythonRunConfiguration) settings.getConfiguration();
    configuration.SCRIPT_NAME = mySourceFile.getVirtualFile().getPath();
    configuration.setName(configuration.suggestedName());
    copyStepsBeforeRun(project, configuration);
    return settings;
  }

  public int compareTo(final Object o) {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
