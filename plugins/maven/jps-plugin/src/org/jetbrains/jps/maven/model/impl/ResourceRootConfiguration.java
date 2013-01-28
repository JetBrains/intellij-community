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

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/12
 */
@Tag("resource")
public class ResourceRootConfiguration {
  @Tag("directory")
  @NotNull
  public String directory;

  @Tag("targetPath")
  @Nullable
  public String targetPath;

  @Attribute("filtered")
  public boolean isFiltered;

  @Tag("includes")
  @AbstractCollection(surroundWithTag =  false, elementTag = "pattern")
  public Set<String> includes = new HashSet<String>();

  @Tag("excludes")
  @AbstractCollection(surroundWithTag =  false, elementTag = "pattern")
  public Set<String> excludes = new HashSet<String>();


  public int computeConfigurationHash() {
    int result = directory.hashCode();
    result = 31 * result + (targetPath != null ? targetPath.hashCode() : 0);
    result = 31 * result + (isFiltered ? 1 : 0);
    //result = 31 * result + includes.hashCode();
    //result = 31 * result + excludes.hashCode();
    return result;
  }
}
