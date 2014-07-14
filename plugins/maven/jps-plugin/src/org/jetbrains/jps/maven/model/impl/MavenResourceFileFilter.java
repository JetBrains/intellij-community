/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;

/**
* @author nik
*/
public class MavenResourceFileFilter implements FileFilter {
  private String[] myNormalizedIncludes;
  private String[] myNormalizedExcludes;
  private FilePattern myFilePattern;
  private File myRoot;

  public MavenResourceFileFilter(@NotNull File rootFile, @NotNull FilePattern filePattern) {
    myFilePattern = filePattern;
    myRoot = rootFile;
  }

  @Override
  public boolean accept(@NotNull File file) {
    final String relPath = FileUtil.getRelativePath(myRoot, file);
    return relPath != null && isIncluded(relPath);
  }

  private boolean isIncluded(String relativePath) {
    if (myNormalizedIncludes == null) {
      if (myFilePattern.includes.isEmpty()) {
        myNormalizedIncludes = new String[]{"**" + File.separatorChar + '*'};
      }
      else {
        myNormalizedIncludes = normalizePatterns(myFilePattern.includes);
      }
    }
    if (myNormalizedExcludes == null) {
      myNormalizedExcludes = normalizePatterns(myFilePattern.excludes);
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
}
