/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.XpathIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class XsltRunConfigType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  public XsltRunConfigType() {
    myFactory = new MyConfigurationFactory(this);
  }

  public static XsltRunConfigType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(XsltRunConfigType.class);
  }

  public String getDisplayName() {
    return "XSLT";
  }

  @NonNls
  @NotNull
  public String getId() {
    return "XSLT";
  }

  public String getConfigurationTypeDescription() {
    return "Run XSLT Script";
  }

  public Icon getIcon() {
    return XpathIcons.Xslt;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  private static class MyConfigurationFactory extends ConfigurationFactory {
    MyConfigurationFactory(XsltRunConfigType type) {
      super(type);
    }

    @NotNull
    public RunConfiguration createTemplateConfiguration(final Project project) {
      return new XsltRunConfiguration(project, this);
    }
  }
}
