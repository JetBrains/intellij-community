// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.PropertiesUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenTestRunningSettings;
import org.jetbrains.idea.maven.utils.MavenFilteredJarUtils;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.jps.maven.model.impl.MavenFilteredJarConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MavenJUnitPatcher extends JUnitPatcher {
  public static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{(.+?)}");
  public static final Pattern ARG_LINE_PATTERN = Pattern.compile("@\\{(.+?)}");
  private static final Logger LOG = Logger.getInstance(MavenJUnitPatcher.class);
  private static final Set<String> EXCLUDE_SUBTAG_NAMES =
    Set.of("classpathDependencyExclude", "classpathDependencyExcludes", "dependencyExclude");
  // See org.apache.maven.artifact.resolver.filter.AbstractScopeArtifactFilter
  private static final Map<String, List<String>> SCOPE_FILTER = Map.of(
    "compile", Arrays.asList("system", "provided", "compile"),
    "runtime", Arrays.asList("compile", "runtime"),
    "compile+runtime", Arrays.asList("system", "provided", "compile", "runtime"),
    "runtime+system", Arrays.asList("system", "compile", "runtime"),
    "test", Arrays.asList("system", "provided", "compile", "runtime", "test"));

  @Override
  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
    if (module == null) return;

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(module.getProject());
    MavenProject mavenProject = projectsManager.findProject(module);
    if (mavenProject == null) return;

    UnaryOperator<String> runtimeProperties = getDynamicConfigurationProperties(module, mavenProject, javaParameters);

    configureFromPlugin(module, javaParameters, mavenProject, runtimeProperties, "maven-surefire-plugin", "surefire");
    configureFromPlugin(module, javaParameters, mavenProject, runtimeProperties, "maven-failsafe-plugin", "failsafe");
    replaceFilteredJarDirectories(projectsManager, module, javaParameters, mavenProject);
  }

  record DependenciesWithClassifiers(MavenId id, List<String> classifiers) {
  }

  record ReplaceInfo(List<MavenFilteredJarConfiguration> configurations, boolean append) {
  }

  private static List<DependenciesWithClassifiers> groupByClassifiers(@NotNull List<MavenArtifact> dependencies) {
    var map = new LinkedHashMap<MavenId, List<String>>();
    dependencies.forEach(d -> {
      map.compute(d.getMavenId(), (i, l) -> {
        if (l == null) {
          return new SmartList<>(d.getClassifier());
        }
        else {
          l.add(d.getClassifier());
          return l;
        }
      });
    });
    return ContainerUtil.map(map.entrySet(), e -> new DependenciesWithClassifiers(e.getKey(), e.getValue()));
  }


  private static void replaceFilteredJarDirectories(MavenProjectsManager projectsManager,
                                                    @NotNull Module module,
                                                    JavaParameters parameters,
                                                    MavenProject project) {
    if (!Registry.is("maven.build.additional.jars")) return;
    List<ReplaceInfo> fixClassPath = getClassPathReplaceInfos(projectsManager, project);

    replaceClassPath(module, parameters, fixClassPath);
  }

  private static void replaceClassPath(@NotNull Module module, JavaParameters parameters, List<ReplaceInfo> fixClassPath) {
    if (fixClassPath.isEmpty()) return;
    String[] paths = ArrayUtil.toStringArray(parameters.getClassPath().getPathList());
    ArrayList<String> resultingPath = new ArrayList<>(paths.length);
    boolean pathFixed = false;
    Map<String, ReplaceInfo> configurationMap = new HashMap<>();
    fixClassPath.forEach(ri -> configurationMap.put(FileUtil.toCanonicalPath(ri.configurations.get(0).originalOutput), ri));

    for (String path : paths) {
      ReplaceInfo replaceInfo = configurationMap.get(FileUtil.toCanonicalPath(path));
      if (replaceInfo != null) {
        pathFixed = true;
        if (replaceInfo.append) {
          resultingPath.add(path);
        }
        resultingPath.addAll(ContainerUtil.map(replaceInfo.configurations, c -> c.jarOutput));
      }
      else {
        resultingPath.add(path);
      }
    }
    if (!pathFixed) {
      LOG.warn(
        "expected to replace " + fixClassPath.size() + " dependencies in running module " + module.getName() + ", but replaced 0");
    }
    else {
      parameters.getClassPath().clear();
      parameters.getClassPath().addAll(resultingPath);
    }
  }

  private static @NotNull List<ReplaceInfo> getClassPathReplaceInfos(MavenProjectsManager projectsManager, MavenProject project) {
    Set<MavenId> visited = new HashSet<>();


    ArrayDeque<DependenciesWithClassifiers> queue = new ArrayDeque<>(groupByClassifiers(project.getDependencies()));

    List<ReplaceInfo> fixClassPath = new ArrayList<>();
    while (!queue.isEmpty()) {
      var dependencies = queue.poll();
      var depProject = projectsManager.findProject(dependencies.id);
      if (depProject == null) continue;
      if (!visited.add(dependencies.id)) continue;
      List<MavenFilteredJarConfiguration> configurations =
        ContainerUtil.mapNotNull(dependencies.classifiers, classifier -> findFilteredJarConfig(projectsManager, depProject, classifier));
      if (!configurations.isEmpty()) {
        fixClassPath.add(new ReplaceInfo(configurations, needAppendClasspath(dependencies.classifiers)));
      }
      queue.addAll(groupByClassifiers(project.getDependencies()));
    }
    return fixClassPath;
  }

  private static boolean needAppendClasspath(List<String> classifiers) {
    for (String c : classifiers) {
      if (c == null || c.isEmpty()) return true;
    }
    return false;
  }


  private static @Nullable MavenFilteredJarConfiguration findFilteredJarConfig(MavenProjectsManager projectsManager,
                                                                               MavenProject mavenProject, String classifier) {
    List<@NotNull MavenFilteredJarConfiguration> configurations =
      MavenFilteredJarUtils.getAllFilteredConfigurations(projectsManager, mavenProject);
    return ContainerUtil.find(configurations, c -> StringUtil.equals(c.classifier, classifier));
  }


  private static void configureFromPlugin(@NotNull Module module,
                                          JavaParameters javaParameters,
                                          MavenProject mavenProject,
                                          UnaryOperator<String> runtimeProperties,
                                          String pluginArtifact,
                                          String pluginName) {
    MavenPlugin plugin = mavenProject.findPlugin("org.apache.maven.plugins", pluginArtifact);
    if (plugin != null) {
      Element config = mavenProject.getPluginGoalConfiguration(plugin, null);
      if (config == null) {
        config = new Element("configuration");
      }
      patchJavaParameters(module, javaParameters, mavenProject, pluginName, config, runtimeProperties);
    }
  }

  private static UnaryOperator<String> getDynamicConfigurationProperties(Module module,
                                                                         MavenProject mavenProject,
                                                                         JavaParameters javaParameters) {
    MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.getFile());
    if (domModel == null) {
      return s -> s;
    }
    var staticProperties = MavenPropertyResolver.collectPropertyMapFromDOM(mavenProject, domModel);
    Properties modelProperties = mavenProject.getProperties();
    String jaCoCoConfigProperty = getJaCoCoArgLineProperty(mavenProject);
    ParametersList vmParameters = javaParameters.getVMParametersList();
    return name -> {
      String vmPropertyValue = vmParameters.getPropertyValue(name);
      if (vmPropertyValue != null) {
        return vmPropertyValue;
      }
      String staticPropertyValue = staticProperties.get(name);
      if (staticPropertyValue != null) {
        return MavenPropertyResolver.resolve(staticPropertyValue, domModel);
      }
      String modelPropertyValue = modelProperties.getProperty(name);
      if (modelPropertyValue != null) {
        return modelPropertyValue;
      }
      if (name.equals(jaCoCoConfigProperty)) {
        return "";
      }
      return null;
    };
  }

  private static String getJaCoCoArgLineProperty(MavenProject mavenProject) {
    String jaCoCoConfigProperty = "argLine";
    Element jaCoCoConfig = mavenProject.getPluginConfiguration("org.jacoco", "jacoco-maven-plugin");
    if (jaCoCoConfig != null) {
      Element propertyName = jaCoCoConfig.getChild("propertyName");
      if (propertyName != null) {
        jaCoCoConfigProperty = propertyName.getTextTrim();
      }
    }
    Element jaCoCoGoalConfig = mavenProject.getPluginGoalConfiguration("org.jacoco", "jacoco-maven-plugin", "prepare-agent");
    if (jaCoCoGoalConfig != null) {
      Element propertyName = jaCoCoGoalConfig.getChild("propertyName");
      if (propertyName != null) {
        jaCoCoConfigProperty = propertyName.getTextTrim();
      }
    }
    return jaCoCoConfigProperty;
  }

  private static void patchJavaParameters(@NotNull Module module,
                                          @NotNull JavaParameters javaParameters,
                                          @NotNull MavenProject mavenProject,
                                          @NotNull String plugin,
                                          @NotNull Element config,
                                          @NotNull UnaryOperator<String> runtimeProperties) {
    MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.getFile());

    MavenTestRunningSettings testRunningSettings = MavenProjectSettings.getInstance(module.getProject()).getTestRunningSettings();

    List<String> paths = MavenJDOMUtil.findChildrenValuesByPath(config, "additionalClasspathElements", "additionalClasspathElement");

    if (!paths.isEmpty()) {
      for (String pathLine : paths) {
        for (String path : pathLine.split(",")) {
          javaParameters.getClassPath().add(resolvePluginProperties(plugin, path.trim(), domModel));
        }
      }
    }

    List<String> excludes = getExcludedCoordinates(config);
    String scopeExclude = MavenJDOMUtil.findChildValueByPath(config, "classpathDependencyScopeExclude");

    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(module.getProject());
    if (scopeExclude != null || !excludes.isEmpty()) {
      for (MavenArtifact dependency : mavenProject.getDependencies()) {
        if (scopeExclude != null && SCOPE_FILTER.getOrDefault(scopeExclude, Collections.emptyList()).contains(dependency.getScope()) ||
            excludes.contains(dependency.getGroupId() + ":" + dependency.getArtifactId())) {
          File file = dependency.getFile();
          javaParameters.getClassPath().remove(file.getAbsolutePath());

          Optional.ofNullable(mavenProjectsManager.findProject(dependency.getMavenId()))
            .map(mavenProjectsManager::findModule)
            .map(CompilerModuleExtension::getInstance)
            .map(CompilerModuleExtension::getCompilerOutputPath)
            .ifPresent(compilerOutputPath -> javaParameters.getClassPath().remove(compilerOutputPath));
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
      String propertyText = argLine != null ? argLine.getTextTrim() : "${argLine}";
      String value = resolvePluginProperties(plugin, propertyText, domModel);
      value = resolveVmProperties(javaParameters.getVMParametersList(), value);
      if (StringUtil.isNotEmpty(value) && isResolved(plugin, value)) {
        value = resolveRuntimeProperties(value, runtimeProperties);
        javaParameters.getVMParametersList().addParametersString(value);
      }
    }
  }

  private static String resolveRuntimeProperties(String value, UnaryOperator<String> runtimeProperties) {
    Matcher matcher = ARG_LINE_PATTERN.matcher(value);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String replacement = runtimeProperties.apply(matcher.group(1));
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  @NotNull
  private static List<String> getExcludedCoordinates(@NotNull Element config) {
    Element excludesElement = config.getChild("classpathDependencyExcludes");
    if (excludesElement == null) {
      return Collections.emptyList();
    }
    String rawText = excludesElement.getTextTrim();
    List<String> excludes = new ArrayList<>();
    if (!rawText.isEmpty()) {
      StreamEx.split(rawText, ',').map(String::trim).into(excludes);
    }
    for (Element child : excludesElement.getChildren()) {
      String name = child.getName();
      if (name != null && EXCLUDE_SUBTAG_NAMES.contains(name)) {
        String excludeItem = child.getTextTrim();
        if (!excludeItem.isEmpty()) {
          StreamEx.split(excludeItem, ',').map(String::trim).into(excludes);
        }
      }
    }
    return excludes;
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
      if (propertyValue == null) continue;
      toReplace.put(finding, propertyValue);
    }
    for (Map.Entry<String, String> entry : toReplace.entrySet()) {
      value = value.replace(entry.getKey(), entry.getValue());
    }

    return value;
  }

  private static boolean isEnabled(String plugin, String s) {
    return !Boolean.parseBoolean(System.getProperty("idea.maven." + plugin + ".disable." + s));
  }

  private static boolean isResolved(String plugin, String s) {
    return !s.contains("${") || Boolean.parseBoolean(System.getProperty("idea.maven." + plugin + ".allPropertiesAreResolved"));
  }
}
