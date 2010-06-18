/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 13-May-2010
 */
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class XsltConfigurationProducer extends RuntimeConfigurationProducer{
  private XmlFile myFile;

  public XsltConfigurationProducer() {
    super(ConfigurationTypeUtil.findConfigurationType(XsltRunConfigType.class));
  }

  @Override
  public PsiElement getSourceElement() {
    return myFile;
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    final XmlFile file = PsiTreeUtil.getParentOfType(location.getPsiElement(), XmlFile.class, false);
    if (file != null && file.isPhysical() && XsltSupport.isXsltFile(file)) {
      myFile = file;
      final Project project = myFile.getProject();
      final RunnerAndConfigurationSettings settings =
        RunManager.getInstance(project).createRunConfiguration(myFile.getName(), getConfigurationFactory());
      ((XsltRunConfiguration)settings.getConfiguration()).initFromFile(myFile);
      return settings;
    }
    return null;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }


  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
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