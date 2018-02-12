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
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenTestRunningSettings;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenJUnitPatcher extends JUnitPatcher {
  public static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");
  private static final Logger LOG = Logger.getInstance(MavenJUnitPatcher.class);

  @Override
  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
    if (module == null) return;

    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    if (mavenProject == null) return;

    Element config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-surefire-plugin");
    if (config != null) {
        patchJavaParameters(module, javaParameters, mavenProject, "surefire", config);
    }
    config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-failsafe-plugin");
    if (config != null) {
        patchJavaParameters(module, javaParameters, mavenProject, "failsafe", config);
    }
  }

  private static void patchJavaParameters(@NotNull Module module,
                                          @NotNull JavaParameters javaParameters,
                                          @NotNull MavenProject mavenProject,
                                          @NotNull String plugin,
                                          @NotNull Element config) {
    MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.getFile());

    MavenTestRunningSettings testRunningSettings = MavenProjectSettings.getInstance(module.getProject()).getTestRunningSettings();

    List<String> paths = MavenJDOMUtil.findChildrenValuesByPath(config, "additionalClasspathElements", "additionalClasspathElement");

    if (paths.size() > 0) {
      for (String path : paths) {
        javaParameters.getClassPath().add(resolvePluginProperties(plugin, path, domModel));
      }
    }

    if (testRunningSettings.isPassSystemProperties()) {
      if (isEnabled(plugin, "systemPropertyVariables")) {
        Element systemPropertyVariables = config.getChild("systemPropertyVariables");
        if (systemPropertyVariables != null) {
          for (Element element : systemPropertyVariables.getChildren()) {
            String propertyName = element.getName();
            if (!javaParameters.getVMParametersList().hasProperty(propertyName)) {
              String value = resolvePluginProperties(plugin, element.getValue(), domModel);
              value = resolveVmProperties(javaParameters.getVMParametersList(), value);
              if (isResolved(plugin, value)) {
                javaParameters.getVMParametersList().addProperty(propertyName, value);
              }
            }
          }
        }
      }
      if (isEnabled(plugin, "systemPropertiesFile")) {
        Element systemPropertiesFile = config.getChild("systemPropertiesFile");
        if (systemPropertiesFile != null) {
          String systemPropertiesFilePath = systemPropertiesFile.getTextTrim();
          if (StringUtil.isNotEmpty(systemPropertiesFilePath) && !FileUtil.isAbsolute(systemPropertiesFilePath)) {
            systemPropertiesFilePath = mavenProject.getDirectory() + '/' + systemPropertiesFilePath;
          }
          if (StringUtil.isNotEmpty(systemPropertiesFilePath) && new File(systemPropertiesFilePath).exists()) {
            try {
              Reader fis = new BufferedReader(new FileReader(systemPropertiesFilePath));
              try {
                Map<String, String> properties = FileUtil.loadProperties(fis);
                properties.forEach((pName, pValue) -> javaParameters.getVMParametersList().addProperty(pName, pValue));
              }
              finally {
                fis.close();
              }
            }
            catch (IOException e) {
              LOG.warn("Can't read property file '" + systemPropertiesFilePath + "': " + e.getMessage());
            }
          }
        }
      }
    }

    if (testRunningSettings.isPassEnvironmentVariables() && isEnabled(plugin, "environmentVariables")) {
      Element environmentVariables = config.getChild("environmentVariables");
      if (environmentVariables != null) {
        for (Element element : environmentVariables.getChildren()) {
          String variableName = element.getName();

          if (!javaParameters.getEnv().containsKey(variableName)) {
            String value = resolvePluginProperties(plugin, element.getValue(), domModel);
            value = resolveVmProperties(javaParameters.getVMParametersList(), value);
            if (isResolved(plugin, value)) {
              javaParameters.addEnv(variableName, value);
            }
          }
        }
      }
    }

    if (testRunningSettings.isPassArgLine() && isEnabled(plugin, "argLine")) {
      Element argLine = config.getChild("argLine");
      if (argLine != null) {
        String value = resolvePluginProperties(plugin, argLine.getTextTrim(), domModel);
        value = resolveVmProperties(javaParameters.getVMParametersList(), value);
        if (StringUtil.isNotEmpty(value) && isResolved(plugin, value)) {
          javaParameters.getVMParametersList().addParametersString(value);
        }
      }
    }
  }

  private static String resolvePluginProperties(@NotNull String plugin, @NotNull String value, @Nullable MavenDomProjectModel domModel) {
    if (domModel != null) {
      value = MavenPropertyResolver.resolve(value, domModel);
    }
    return value.replaceAll("\\$\\{" + plugin + "\\.(forkNumber|threadNumber)\\}", "1");
  }

  private static String resolveVmProperties(@NotNull ParametersList vmParameters, @NotNull String value) {
    Matcher matcher = PROPERTY_PATTERN.matcher(value);
    Map<String, String> toReplace = ContainerUtil.newHashMap();
    while (matcher.find()) {
      String finding = matcher.group();
      final String propertyValue = vmParameters.getPropertyValue(finding.substring(2, finding.length() - 1));
      if(propertyValue == null) continue;
      toReplace.put(finding, propertyValue);
    }
    for (Map.Entry<String, String> entry : toReplace.entrySet()) {
      value = value.replace(entry.getKey(), entry.getValue());
    }

    return value;
  }

  private static boolean isEnabled(String plugin, String s) {
    return !Boolean.valueOf(System.getProperty("idea.maven." + plugin + ".disable." + s));
  }

  private static boolean isResolved(String plugin, String s) {
    return !s.contains("${") || Boolean.valueOf(System.getProperty("idea.maven." + plugin + ".allPropertiesAreResolved"));
  }
}
