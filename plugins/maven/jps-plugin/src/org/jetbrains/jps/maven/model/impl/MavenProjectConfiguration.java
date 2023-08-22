// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 */
public final class MavenProjectConfiguration {
  public static final String CONFIGURATION_FILE_RELATIVE_PATH = "maven/configuration.xml";
  public static final String DEFAULT_ESCAPE_STRING = "\\";
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("-D(\\S+?)=(.+)");
  private static final Pattern MAVEN_PROPERTY_PATTERN = Pattern.compile("-D(\\S+?)(?:=(.+))?");
  public static final Set<String> DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
  static {
    Set<String> set = CollectionFactory.createFilePathSet();
    set.addAll(Arrays.asList("jpg", "jpeg", "gif", "bmp", "png"));
    DEFAULT_FILTERING_EXCLUDED_EXTENSIONS = Collections.unmodifiableSet(set);
  }

  @Tag("resource-processing")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "maven-module", keyAttributeName = "name")
  public Map<String, MavenModuleResourceConfiguration> moduleConfigurations = new HashMap<>();

  @Tag("web-artifact-cfg")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "artifact", keyAttributeName = "name")
  public Map<String, MavenWebArtifactConfiguration> webArtifactConfigs = new HashMap<>();

  @Tag("ejb-client-artifact-cfg")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "artifact", keyAttributeName = "name")
  public Map<String, MavenEjbClientConfiguration> ejbClientArtifactConfigs = new HashMap<>();

  @Nullable
  private MavenModuleResourceConfiguration findProject(MavenIdBean id) {
    return getModuleConfigurationMap().get(id);
  }

  @Transient
  private volatile Map<MavenIdBean, MavenModuleResourceConfiguration> myIdToModuleMap;

  @NotNull
  private Map<MavenIdBean, MavenModuleResourceConfiguration> getModuleConfigurationMap() {
    Map<MavenIdBean, MavenModuleResourceConfiguration> map = myIdToModuleMap;
    if (map == null) {
      map = new HashMap<>();
      for (MavenModuleResourceConfiguration configuration : moduleConfigurations.values()) {
        if (configuration != null) {
          map.put(configuration.id, configuration);
        }
      }
      myIdToModuleMap = map;
    }
    return map;
  }

  @Nullable
  public String resolveProperty(final String propName, final MavenModuleResourceConfiguration moduleConfig, Map<String, String> additionalProperties) {
    boolean hasPrefix = false;
    String unprefixed = propName;

    if (propName.startsWith("pom.")) {
      unprefixed = propName.substring("pom.".length());
      hasPrefix = true;
    }
    else if (propName.startsWith("project.")) {
      unprefixed = propName.substring("project.".length());
      hasPrefix = true;
    }

    MavenModuleResourceConfiguration selectedConfig = moduleConfig;

    while (unprefixed.startsWith("parent.")) {
      MavenIdBean parentId = selectedConfig.parentId;
      if (parentId == null) {
        return null;
      }

      unprefixed = unprefixed.substring("parent.".length());

      if (unprefixed.equals("groupId")) {
        return parentId.groupId;
      }
      if (unprefixed.equals("artifactId")) {
        return parentId.artifactId;
      }

      selectedConfig = findProject(parentId);
      if (selectedConfig == null) {
        return null;
      }
    }

    if (unprefixed.equals("basedir") || (hasPrefix && moduleConfig == selectedConfig && unprefixed.equals("baseUri"))) {
      return selectedConfig.directory;
    }

    String result;

    result = getMavenOptsProperties().get(propName);
    if (result != null) {
      return result;
    }

    result = getMavenAndJvmConfig(selectedConfig).get(propName);
    if (result != null) {
      return result;
    }

    result = getSystemProperties().getProperty(propName);
    if (result != null) {
      return result;
    }

    result = selectedConfig.modelMap.get(unprefixed);
    if (result != null) {
      return result;
    }

    result = additionalProperties.get(propName);
    if (result != null) {
      return result;
    }

    return moduleConfig.properties.get(propName);
  }


  private static volatile Map<String, String> ourPropertiesFromMvnOpts;
  @NotNull
  private static Map<String, String> getMavenOptsProperties() {
    Map<String, String> res = ourPropertiesFromMvnOpts;
    if (res == null) {
      String mavenOpts = System.getenv("MAVEN_OPTS");
      if (mavenOpts != null) {
        res = new HashMap<>();
        final String[] split = ParametersListUtil.parseToArray(mavenOpts);
        for (String parameter : split) {
          final Matcher matcher = PROPERTY_PATTERN.matcher(parameter);
          if (matcher.matches()) {
            res.put(matcher.group(1), matcher.group(2));
          }
        }
      }
      else {
        res = Collections.emptyMap();
      }

      ourPropertiesFromMvnOpts = res;
    }

    return res;
  }

  private static volatile Properties ourSystemProperties;

  public static Properties getSystemProperties() {
    Properties res = ourSystemProperties;
    if (res == null) {
      res = new Properties();
      res.putAll(System.getProperties());

      for (Iterator<Object> itr = res.keySet().iterator(); itr.hasNext(); ) {
        final String propertyName = itr.next().toString();
        if (propertyName.startsWith("idea.") || propertyName.startsWith("jps.")) {
          itr.remove();
        }
      }

      for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
        String key = entry.getKey();
        if (key.startsWith("=")) {
          continue;
        }
        if (SystemInfo.isWindows) {
          key = StringUtil.toUpperCase(key);
        }
        res.setProperty("env." + key, entry.getValue());
      }

      ourSystemProperties = res;
    }
    return res;
  }


  private static final Map<File, Map<String, String>> ourMavenAndJvmConfigs = new ConcurrentHashMap<>();

  // adapted from org.jetbrains.idea.maven.server.Maven3ServerEmbedder
  private static Map<String, String> getMavenAndJvmConfig(MavenModuleResourceConfiguration moduleResourceConfig) {
    return ourMavenAndJvmConfigs.computeIfAbsent(getBaseDir(moduleResourceConfig.directory), baseDir -> readConfigFiles(baseDir));
  }

  @NotNull
  public static Map<String, String> readConfigFiles(File baseDir) {
    Map<String, String> result = new HashMap<>();
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "jvm.config", result, "");
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "maven.config", result, "true");
    return result.isEmpty() ? Collections.emptyMap() : result;
  }

  private static void readConfigFile(File baseDir, String relativePath, Map<String, String> result, String valueIfMissing) {
    File configFile = new File(baseDir, relativePath);

    if (configFile.isFile()) {
      try {
        for (String parameter : ParametersListUtil.parse(FileUtil.loadFile(configFile, CharsetToolkit.UTF8))) {
          Matcher matcher = MAVEN_PROPERTY_PATTERN.matcher(parameter);
          if (matcher.matches()) {
            result.put(matcher.group(1), StringUtil.notNullize(matcher.group(2), valueIfMissing));
          }
        }
      }
      catch (IOException ignore) {
      }
    }
  }

  private static File getBaseDir(String path) {
    File workingDir = new File(FileUtil.toSystemDependentName(path));

    File baseDir = workingDir;
    File dir = workingDir;
    while ((dir = dir.getParentFile()) != null) {
      if (new File(dir, ".mvn").exists()) {
        baseDir = dir;
        break;
      }
    }
    try {
      return baseDir.getCanonicalFile();
    }
    catch (IOException e) {
      return baseDir.getAbsoluteFile();
    }
  }
}
