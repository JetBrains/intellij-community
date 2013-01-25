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
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;

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
  private String[] myNormalizedIncludes;
  private String[] myNormalizedExcludes;

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

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    return new FileFilter() {
      @Override
      public boolean accept(File file) {
        final String relPath = FileUtil.getRelativePath(getRootFile(), file);
        return relPath != null && isIncluded(relPath);
      }
    };
  }

  public boolean isIncluded(String relativePath) {
    if (myNormalizedIncludes == null) {
      if (myConfig.includes.isEmpty()) {
        myNormalizedIncludes = new String[]{"**" + File.separatorChar + '*'};
      }
      else {
        myNormalizedIncludes = normalizePatterns(myConfig.includes);
      }
    }
    if (myNormalizedExcludes == null) {
      myNormalizedExcludes = normalizePatterns(myConfig.excludes);
    }
    return isIncluded(relativePath, myNormalizedIncludes, myNormalizedExcludes);
  }

  private static boolean isIncluded(String relativeName, String[] includes, String[] excludes) {
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

  @NotNull
  private static String[] normalizePatterns(@NotNull Collection<String> patterns) {
    String[] res = new String[patterns.size()];

    int i = 0;
    for (String pattern : patterns) {
      res[i++] = normalizePattern(pattern);
    }

    return res;
  }

  /*
   * Copy-pasted from org.codehaus.plexus.util.AbstractScanner#normalizePattern()
   */
  private static String normalizePattern(String pattern) {
    pattern = pattern.trim();

    if (pattern.startsWith(SelectorUtils.REGEX_HANDLER_PREFIX)) {
      if (File.separatorChar == '\\') {
        pattern = StringUtils.replace(pattern, "/", "\\\\");
      }
      else {
        pattern = StringUtils.replace(pattern, "\\\\", "/");
      }
    }
    else {
      pattern = pattern.replace(File.separatorChar == '/' ? '\\' : '/', File.separatorChar);

      if (pattern.endsWith(File.separator)) {
        pattern += "**";
      }
    }

    return pattern;
  }

  @Override
  public boolean canUseFileCache() {
    return true;
  }
}
