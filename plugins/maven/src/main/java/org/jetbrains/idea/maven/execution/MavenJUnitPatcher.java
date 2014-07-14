/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenTestRunningSettings;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenJUnitPatcher extends JUnitPatcher {

  @Override
  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
    if (module == null) return;

    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    if (mavenProject == null) return;

    Element config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-surefire-plugin");
    if (config == null) return;

    MavenTestRunningSettings testRunningSettings = MavenProjectSettings.getInstance(module.getProject()).getTestRunningSettings();

    List<String> paths = MavenJDOMUtil.findChildrenValuesByPath(config, "additionalClasspathElements", "additionalClasspathElement");

    if (paths.size() > 0) {
      MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.getFile());

      for (String path : paths) {
        if (domModel != null) {
          path = MavenPropertyResolver.resolve(path, domModel);
        }

        javaParameters.getClassPath().add(resolveSurefireProperties(path));
      }
    }

    if (testRunningSettings.isPassEnvironmentVariables() && isEnabled("systemPropertyVariables")) {
      Element systemPropertyVariables = config.getChild("systemPropertyVariables");
      if (systemPropertyVariables != null) {
        for (Element element : systemPropertyVariables.getChildren()) {
          String propertyName = element.getName();

          if (!javaParameters.getVMParametersList().hasProperty(propertyName)) {
            String value = resolveSurefireProperties(element.getValue());
            if (isResolved(value)) {
              javaParameters.getVMParametersList().addProperty(propertyName, value);
            }
          }
        }
      }
    }

    if (testRunningSettings.isPassEnvironmentVariables() && isEnabled("environmentVariables")) {
      Element environmentVariables = config.getChild("environmentVariables");
      if (environmentVariables != null) {
        for (Element element : environmentVariables.getChildren()) {
          String variableName = element.getName();

          if (!javaParameters.getEnv().containsKey(variableName)) {
            String value = resolveSurefireProperties(element.getValue());
            if (isResolved(value)) {
              javaParameters.addEnv(variableName, value);
            }
          }
        }
      }
    }

    if (testRunningSettings.isPassArgLine() && isEnabled("argLine")) {
      Element argLine = config.getChild("argLine");
      if (argLine != null) {
        String value = resolveSurefireProperties(argLine.getTextTrim());
        if (StringUtil.isNotEmpty(value) && isResolved(value)) {
          javaParameters.getVMParametersList().addParametersString(value);
        }
      }
    }
  }

  private static String resolveSurefireProperties(String value) {
    return value.replaceAll("\\$\\{surefire\\.(forkNumber|threadNumber)\\}", "1");
  }

  private static boolean isEnabled(String s) {
    return !Boolean.valueOf(System.getProperty("idea.maven.surefire.disable." + s));
  }

  private static boolean isResolved(String s) {
    return !s.contains("${") || Boolean.valueOf(System.getProperty("idea.maven.surefire.allPropertiesAreResolved"));
  }
}
