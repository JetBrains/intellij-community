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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.*;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/12
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
  public Map<String, String> modelMap = new HashMap<String, String>();

  @Tag("properties")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, String> properties = new HashMap<String, String>();

  @Tag("filtering-excluded-extensions")
  @AbstractCollection(surroundWithTag = false, elementTag = "extension")
  public Set<String> filteringExclusions = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

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

  @Tag("resources")
  @AbstractCollection(surroundWithTag = false, elementTag = "resource")
  public List<ResourceRootConfiguration> resources = new ArrayList<ResourceRootConfiguration>();

  @Tag("test-resources")
  @AbstractCollection(surroundWithTag = false, elementTag = "resource")
  public List<ResourceRootConfiguration> testResources = new ArrayList<ResourceRootConfiguration>();

  public Set<String> getFilteringExcludedExtensions() {
    if (filteringExclusions.isEmpty()) {
      return MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
    }
    final Set<String> result = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
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



