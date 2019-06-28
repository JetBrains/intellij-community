// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Vladislav.Soroka
 */
public class MavenPatternFileFilter implements FileFilter {
  private final String[] myNormalizedIncludes;
  private final String[] myNormalizedExcludes;

  public MavenPatternFileFilter(@Nullable String includes, @Nullable String excludes) {
    this(includes == null ? Collections.emptyList() : StringUtil.split(includes, ","),
         excludes == null ? Collections.emptyList() : StringUtil.split(excludes, ","));
  }

  public MavenPatternFileFilter(@Nullable Collection<String> includes, @Nullable Collection<String> excludes) {
    myNormalizedIncludes = normalizePatterns(includes);
    myNormalizedExcludes = normalizePatterns(excludes);
  }

  @Override
  public boolean accept(File pathname) {
    return accept(pathname.getPath());
  }

  public boolean accept(String path) {
    return accept(path, myNormalizedIncludes, myNormalizedExcludes);
  }

  private static boolean accept(String path, String[] includes, String[] excludes) {
    boolean isIncluded = includes.length == 0;
    for (String each : includes) {
      if (SelectorUtils.matchPath(each, path)) {
        isIncluded = true;
        break;
      }
    }
    if (!isIncluded) {
      return false;
    }
    for (String each : excludes) {
      if (SelectorUtils.matchPath(each, path)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static String[] normalizePatterns(@Nullable Collection<String> patterns) {
    if (ContainerUtil.isEmpty(patterns)) return ArrayUtilRt.EMPTY_STRING_ARRAY;

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
