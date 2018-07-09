/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class MavenModuleResourceConfiguration {
  @NotNull
  @Tag("id")
  public MavenIdBean id;

  @Nullable
  @Tag("parentId")
  public MavenIdBean parentId;

  @NotNull
  @Tag("directory")
  public String directory;

  @Nullable
  @Tag("manifest")
  public String manifest;

  @Nullable
  @Tag("classpath")
  public String classpath;

  @NotNull
  @Tag("delimiters-pattern")
  public String delimitersPattern;

  @Tag("model-map")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, String> modelMap = new HashMap<>();

  @Tag("properties")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, String> properties = new HashMap<>();

  @XCollection(propertyElementName = "filtering-excluded-extensions", elementName = "extension")
  public Set<String> filteringExclusions = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);

  @OptionTag
  public String escapeString = null;

  @OptionTag
  public boolean escapeWindowsPaths = true;

  @OptionTag
  public boolean overwrite;

  @OptionTag
  public String outputDirectory = null;

  @OptionTag
  public String testOutputDirectory = null;

  @XCollection(propertyElementName = "resources", elementName = "resource")
  public List<ResourceRootConfiguration> resources = new ArrayList<>();

  @XCollection(propertyElementName = "test-resources", elementName = "resource")
  public List<ResourceRootConfiguration> testResources = new ArrayList<>();

  public Set<String> getFilteringExcludedExtensions() {
    if (filteringExclusions.isEmpty()) {
      return MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
    }
    final Set<String> result = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);
    result.addAll(MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS);
    result.addAll(filteringExclusions);
    return Collections.unmodifiableSet(result);
  }

  public int computeConfigurationHash(boolean forTestResources) {
    int result = computeModuleConfigurationHash();

    final List<ResourceRootConfiguration> _resources = forTestResources? testResources : resources;
    result = 31 * result;
    for (ResourceRootConfiguration resource : _resources) {
      result += resource.computeConfigurationHash();
    }
    return result;
  }

  public int computeModuleConfigurationHash() {
    int result = id.hashCode();
    result = 31 * result + (parentId != null ? parentId.hashCode() : 0);
    result = 31 * result + directory.hashCode();
    result = 31 * result + (manifest != null ? manifest.hashCode() : 0);
    result = 31 * result + (classpath != null ? classpath.hashCode() : 0);
    result = 31 * result + delimitersPattern.hashCode();
    result = 31 * result + modelMap.hashCode();
    result = 31 * result + properties.hashCode();
    result = 31 * result + filteringExclusions.hashCode();
    result = 31 * result + (escapeString != null ? escapeString.hashCode() : 0);
    result = 31 * result + (outputDirectory != null ? outputDirectory.hashCode() : 0);
    result = 31 * result + (testOutputDirectory != null ? testOutputDirectory.hashCode() : 0);
    result = 31 * result + (escapeWindowsPaths ? 1 : 0);
    result = 31 * result + (overwrite ? 1 : 0);
    return result;
  }
}



