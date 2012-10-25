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
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import gnu.trove.THashSet;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/24/12
 */
public class MavenProjectConfiguration {
  public static final String CONFIGURATION_FILE_RELATIVE_PATH = "maven/configuration.xml";
  public static final String DEFAULT_ESCAPE_STRING = "\\";
  public static final String DEFAULT_INCLUDE_PATTERN = FileUtil.convertAntToRegexp("**/*");
  public static final Set<String> DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
  static {
    final THashSet<String> set = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    set.addAll(Arrays.asList("jpg", "jpeg", "gif", "bmp", "png"));
    DEFAULT_FILTERING_EXCLUDED_EXTENSIONS = Collections.unmodifiableSet(set);
  }

  @Tag("resource-processing")
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "maven-module", keyAttributeName = "name")
  public Map<String, MavenModuleResourceConfiguration> moduleConfigurations = new HashMap<String, MavenModuleResourceConfiguration>();
}
