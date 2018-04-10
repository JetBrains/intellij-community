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
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 */
public class MavenProjectConfiguration {
  public static final String CONFIGURATION_FILE_RELATIVE_PATH = "maven/configuration.xml";
  public static final String DEFAULT_ESCAPE_STRING = "\\";
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("-D(\\S+?)=(.+)");
  public static final Set<String> DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
  static {
    final THashSet<String> set = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);
    set.addAll(Arrays.asList("jpg", "jpeg", "gif", "bmp", "png"));
    DEFAULT_FILTERING_EXCLUDED_EXTENSIONS = Collections.unmodifiableSet(set);
  }

  @Tag("resource-processing")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "maven-module", keyAttributeName = "name")
  public Map<String, MavenModuleResourceConfiguration> moduleConfigurations = new THashMap<>();

  @Tag("web-artifact-cfg")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "artifact", keyAttributeName = "name")
  public Map<String, MavenWebArtifactConfiguration> webArtifactConfigs = new THashMap<>();

  @Tag("ejb-client-artifact-cfg")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "artifact", keyAttributeName = "name")
  public Map<String, MavenEjbClientConfiguration> ejbClientArtifactConfigs = new THashMap<>();

  @Nullable
  public MavenModuleResourceConfiguration findProject(MavenIdBean id) {
    return getModuleConfigurationMap().get(id);
  }

  @Transient
  private volatile Map<MavenIdBean, MavenModuleResourceConfiguration> myIdToModuleMap;

  @NotNull
  private Map<MavenIdBean, MavenModuleResourceConfiguration> getModuleConfigurationMap() {
    Map<MavenIdBean, MavenModuleResourceConfiguration> map = myIdToModuleMap;
    if (map == null) {
      map = new THashMap<>();
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
          key = key.toUpperCase();
        }
        res.setProperty("env." + key, entry.getValue());
      }

      ourSystemProperties = res;
    }
    return res;
  }
}
