// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.PropertiesUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenTestRunningSettings;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenJUnitPatcher extends JUnitPatcher {
  public static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{(.+?)}");
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

    List<String> excludes = MavenJDOMUtil.findChildrenValuesByPath(config, "classpathDependencyExcludes", "classpathDependencyExclude");
    String scopeExclude = MavenJDOMUtil.findChildValueByPath(config, "classpathDependencyScopeExclude");

    if (scopeExclude != null || !excludes.isEmpty()) {
      for (MavenArtifact dependency : mavenProject.getDependencies()) {
        if (scopeExclude != null && scopeExclude.equals(dependency.getScope()) ||
            excludes.contains(dependency.getGroupId() + ":" + dependency.getArtifactId())) {
          File file = dependency.getFile();
          javaParameters.getClassPath().remove(file.getAbsolutePath());
        }
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
            try (Reader fis = Files.newBufferedReader(Paths.get(systemPropertiesFilePath), StandardCharsets.ISO_8859_1)) {
              Map<String, String> properties = PropertiesUtil.loadProperties(fis);
              properties.forEach((pName, pValue) -> javaParameters.getVMParametersList().addProperty(pName, pValue));
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
          if (value.contains("@{argLine}")) {
            String parametersString = javaParameters.getVMParametersList().getParametersString();
            javaParameters.getVMParametersList().clearAll();
            javaParameters.getVMParametersList().addParametersString(StringUtil.replace(value, "@{argLine}", parametersString));
          }
          else {
            javaParameters.getVMParametersList().addParametersString(value);
          }
        }
      }
    }
  }

  private static String resolvePluginProperties(@NotNull String plugin, @NotNull String value, @Nullable MavenDomProjectModel domModel) {
    if (domModel != null) {
      value = MavenPropertyResolver.resolve(value, domModel);
    }
    return value.replaceAll("\\$\\{" + plugin + "\\.(forkNumber|threadNumber)}", "1");
  }

  private static String resolveVmProperties(@NotNull ParametersList vmParameters, @NotNull String value) {
    Matcher matcher = PROPERTY_PATTERN.matcher(value);
    Map<String, String> toReplace = new HashMap<>();
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
