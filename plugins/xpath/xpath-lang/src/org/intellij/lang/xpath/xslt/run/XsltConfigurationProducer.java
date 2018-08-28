// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class XsltConfigurationProducer extends RuntimeConfigurationProducer{
  public XsltConfigurationProducer() {
    super(XsltRunConfigType.getInstance());
  }

  @Override
  public PsiElement getSourceElement() {
    return restoreSourceElement();
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    final XmlFile file = PsiTreeUtil.getParentOfType(location.getPsiElement(), XmlFile.class, false);
    if (file != null && file.isPhysical() && XsltSupport.isXsltFile(file)) {
      storeSourceElement(file);
      final Project project = file.getProject();
      final RunnerAndConfigurationSettings settings =
        RunManager.getInstance(project).createRunConfiguration(file.getName(), getConfigurationFactory());
      ((XsltRunConfiguration)settings.getConfiguration()).initFromFile(file);
      return settings;
    }
    return null;
  }

  @Override
  public int compareTo(Object o) {
    return PREFERED;
  }


  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull List<RunnerAndConfigurationSettings> existingConfigurations,
                                                                 ConfigurationContext context) {
    final XmlFile file = PsiTreeUtil.getParentOfType(location.getPsiElement(), XmlFile.class, false);
    if (file != null && file.isPhysical() && XsltSupport.isXsltFile(file)) {
      for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
        final RunConfiguration configuration = existingConfiguration.getConfiguration();
        if (configuration instanceof XsltRunConfiguration) {
          if (file.getVirtualFile().getPath().replace('/', File.separatorChar)
            .equals(((XsltRunConfiguration)configuration).getXsltFile())) {
            return existingConfiguration;
          }
        }
      }
    }
    return null;
  }
}