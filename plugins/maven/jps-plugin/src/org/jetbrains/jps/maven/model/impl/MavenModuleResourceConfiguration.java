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
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import gnu.trove.THashSet;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/12
 */
public class MavenModuleResourceConfiguration {
  @Tag("properties")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, String> myProperties = new HashMap<String, String>();

  @Tag("filtering-excluded-extensions")
  @AbstractCollection(surroundWithTag = false, elementTag = "extension")
  public Set<String> myFilteringExcludedExtensions = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

  @OptionTag
  public String escapeString = MavenProjectConfiguration.DEFAULT_ESCAPE_STRING;

  @Tag("resources")
  @AbstractCollection(surroundWithTag = false, elementTag = "resource")
  public List<ResourceRootConfiguration> myResources = new ArrayList<ResourceRootConfiguration>();

  @Tag("test-resources")
  @AbstractCollection(surroundWithTag = false, elementTag = "resource")
  public List<ResourceRootConfiguration> myTestResources = new ArrayList<ResourceRootConfiguration>();


  public Set<String> getFiltetingExcludedExtensions() {
    if (myFilteringExcludedExtensions.isEmpty()) {
      return MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
    }
    final Set<String> result = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    result.addAll(MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS);
    result.addAll(myFilteringExcludedExtensions);
    return Collections.unmodifiableSet(result);
  }
}



