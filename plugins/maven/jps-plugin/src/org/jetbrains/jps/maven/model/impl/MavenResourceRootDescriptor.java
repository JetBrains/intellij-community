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
import org.codehaus.plexus.util.SelectorUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/21/12
 */
public class MavenResourceRootDescriptor extends BuildRootDescriptor {
  private final MavenResourcesTarget myTarget;
  private final ResourceRootConfiguration myConfig;
  private final File myFile;
  private final String myId;

  public MavenResourceRootDescriptor(@NotNull MavenResourcesTarget target, ResourceRootConfiguration config) {
    myTarget = target;
    myConfig = config;
    final String path = FileUtil.toCanonicalPath(config.directory);
    myFile = new File(path);
    myId = path;
  }

  public ResourceRootConfiguration getConfiguration() {
    return myConfig;
  }

  @Override
  public String getRootId() {
    return myId;
  }

  @Override
  public File getRootFile() {
    return myFile;
  }

  @Override
  public MavenResourcesTarget getTarget() {
    return myTarget;
  }

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return true;
      }
    };
  }

  public boolean isIncluded(String relativePath) {
    return isIncluded(FileUtil.toSystemIndependentName(relativePath),
                      myConfig.includes.isEmpty() ? MavenProjectConfiguration.DEFAULT_INCLUDES : myConfig.includes,
                      myConfig.excludes);
  }

  private static boolean isIncluded(String relativeName, Collection<String> includes, Collection<String> excludes) {
    boolean isIncluded = false;
    for (String each : includes) {
      if (SelectorUtils.matchPath(each, relativeName)) {
        isIncluded = true;
        break;
      }
    }
    if (!isIncluded) {
      return false;
    }
    for (String each : excludes) {
      if (SelectorUtils.matchPath(each, relativeName)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean canUseFileCache() {
    return true;
  }
}
