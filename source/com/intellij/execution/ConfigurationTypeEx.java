package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 19, 2004
 */
public interface ConfigurationTypeEx extends ConfigurationType{
  RunnerAndConfigurationSettings createConfigurationByLocation(Location location);
  boolean isConfigurationByElement(RunConfiguration configuration, Project project, PsiElement element);
}
