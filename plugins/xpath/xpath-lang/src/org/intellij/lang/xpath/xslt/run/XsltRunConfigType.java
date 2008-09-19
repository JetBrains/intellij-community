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

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.xslt.XsltSupport;

import javax.swing.*;
import java.io.File;

public class XsltRunConfigType implements ConfigurationType, LocatableConfigurationType {
    private final ConfigurationFactory myFactory = new MyConfigurationFactory();

    public XsltRunConfigType() {
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
        return IconLoader.getIcon("/icons/xslt.png");
    }

    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{ myFactory };
    }

    @NotNull
    public String getComponentName() {
        return "XSLT-Support.RunConfigType";
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    private class MyConfigurationFactory extends ConfigurationFactory {
        public MyConfigurationFactory() {
            super(XsltRunConfigType.this);
        }

        public RunConfiguration createTemplateConfiguration(final Project project) {
            return new XsltRunConfiguration(project, this);
        }
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
        final XmlFile file = PsiTreeUtil.getParentOfType(location.getPsiElement(), XmlFile.class, false);
        if (file != null) {
            if (file.isPhysical() && XsltSupport.isXsltFile(file)) {
                final Project project = file.getProject();
                return RunManager.getInstance(project).createRunConfiguration(file.getName(), new MyConfigurationFactory() {
                    @Override
                    public RunConfiguration createConfiguration(String name, RunConfiguration template) {
                        return ((XsltRunConfiguration)super.createConfiguration(name, template)).initFromFile(file);
                    }
                });
            }
        }
        //noinspection ConstantConditions
        return null;
    }

    public boolean isConfigurationByLocation(RunConfiguration runConfiguration, Location location) {
        return isConfigurationByElement(runConfiguration, location.getProject(), location.getPsiElement());
    }

    public boolean isConfigurationByElement(RunConfiguration configuration, Project project, PsiElement element) {
        final XmlFile file = PsiTreeUtil.getParentOfType(element, XmlFile.class, false);
        if (configuration instanceof XsltRunConfiguration) {
            if (file != null && file.isPhysical() && XsltSupport.isXsltFile(file)) {
                //noinspection ConstantConditions
                return file.getVirtualFile().getPath().replace('/', File.separatorChar).equals(((XsltRunConfiguration)configuration).getXsltFile());
            }
        }
        return false;
    }
}
