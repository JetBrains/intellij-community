// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import org.jdom.Element;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.function.Consumer;

public class BuildHelperMavenPluginUtil {
  public static void addBuilderHelperPaths(MavenProject mavenProject, String goal, Consumer<String> addFolder) {
    final MavenPlugin plugin = mavenProject.findPlugin("org.codehaus.mojo", "build-helper-maven-plugin");
    if (plugin != null) {
      for (MavenPlugin.Execution execution : plugin.getExecutions()) {
        if (execution.getGoals().contains(goal)) {
          final Element configurationElement = execution.getConfigurationElement();
          if (configurationElement != null) {
            final Element sourcesElement = configurationElement.getChild("sources");
            if (sourcesElement != null) {
              for (Element element : sourcesElement.getChildren()) {
                addFolder.accept(element.getTextTrim());
              }
            }
          }
        }
      }
    }
  }

  public static void addBuilderHelperResourcesPaths(MavenProject mavenProject, String goal, Consumer<String> addFolder) {
    final MavenPlugin plugin = mavenProject.findPlugin("org.codehaus.mojo", "build-helper-maven-plugin");
    if (plugin != null) {
      for (MavenPlugin.Execution execution : plugin.getExecutions()) {
        if (execution.getGoals().contains(goal)) {
          final Element configurationElement = execution.getConfigurationElement();
          if (configurationElement != null) {
            final Element sourcesElement = configurationElement.getChild("resources");
            if (sourcesElement != null) {
              for (Element element : sourcesElement.getChildren()) {
                Element directory = element.getChild("directory");
                if (directory != null) addFolder.accept(directory.getTextTrim());
              }
            }
          }
        }
      }
    }
  }
}
