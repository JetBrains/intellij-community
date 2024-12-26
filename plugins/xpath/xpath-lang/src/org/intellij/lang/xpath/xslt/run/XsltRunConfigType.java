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
import com.intellij.openapi.util.NlsSafe;
import icons.XpathIcons;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class XsltRunConfigType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  public XsltRunConfigType() {
    myFactory = new ConfigurationFactory(this) {
      @Override
      public @NotNull RunConfiguration createTemplateConfiguration(final @NotNull Project project) {
        return new XsltRunConfiguration(project, this);
      }

      @Override
      public @NotNull String getId() {
        return "XSLT";
      }
    };
  }

  public static XsltRunConfigType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(XsltRunConfigType.class);
  }

  @Override
  public @NotNull String getDisplayName() {
    return getId();
  }

  @Override
  public @NotNull @NlsSafe String getId() {
    return "XSLT";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return XPathBundle.message("run.configuration.description.xslt.script");
  }

  @Override
  public Icon getIcon() {
    return XpathIcons.Xslt;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.XSLT";
  }
}
