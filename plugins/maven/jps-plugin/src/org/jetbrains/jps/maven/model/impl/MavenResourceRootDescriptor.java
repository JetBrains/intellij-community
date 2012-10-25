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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.indices.IgnoredFileIndex;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/21/12
 */
public class MavenResourceRootDescriptor extends BuildRootDescriptor {
  private final MavenResourcesTarget myTarget;
  private final ResourceRootConfiguration myConfig;
  private final IgnoredFileIndex myIgnoredFileIndex;
  private final File myFile;
  private final String myId;

  public MavenResourceRootDescriptor(@NotNull MavenResourcesTarget target,
                                     ResourceRootConfiguration config,
                                     IgnoredFileIndex ignoredFileIndex) {
    myTarget = target;
    myConfig = config;
    myIgnoredFileIndex = ignoredFileIndex;
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
  public FileFilter createFileFilter() {
    return new MyFileFilter(myFile, myConfig.includes, myConfig.excludes, myIgnoredFileIndex);
  }

  private static class MyFileFilter implements FileFilter {
    private final List<Pattern> myCompiledIncludes;
    private final List<Pattern> myCompiledExcludes;
    private final File mySourceRoot;
    private final IgnoredFileIndex myIgnoredFileIndex;

    private MyFileFilter(File sourceRoot, final List<String> includes, final List<String> excludes, IgnoredFileIndex ignoredFileIndex) {
      mySourceRoot = sourceRoot;
      myIgnoredFileIndex = ignoredFileIndex;
      myCompiledIncludes = compilePatterns(includes, MavenProjectConfiguration.DEFAULT_INCLUDE_PATTERN);
      myCompiledExcludes = compilePatterns(excludes, null);
    }

    @Override
    public boolean accept(File file) {
      if (myIgnoredFileIndex.isIgnored(file.getName())) {
        return false;
      }
      final String relativePath = FileUtil.getRelativePath(mySourceRoot, file);
      if (relativePath == null) {
        return false;
      }
      return isIncluded(FileUtil.toSystemIndependentName(relativePath), myCompiledIncludes, myCompiledExcludes);
    }

    private static boolean isIncluded(String relativeName, List<Pattern> includes, List<Pattern> excludes) {
      boolean isIncluded = false;
      for (Pattern each : includes) {
        if (each.matcher(relativeName).matches()) {
          isIncluded = true;
          break;
        }
      }
      if (!isIncluded) {
        return false;
      }
      for (Pattern each : excludes) {
        if (each.matcher(relativeName).matches()) {
          return false;
        }
      }
      return true;
    }


    @NotNull
    private static List<Pattern> compilePatterns(@NotNull List<String> patterns, @Nullable String defaultValue) {
      final List<Pattern> result = new ArrayList<Pattern>();
      if (patterns.isEmpty()) {
        if (defaultValue == null) {
          return Collections.emptyList();
        }
        try {
          result.add(Pattern.compile(defaultValue));
        }
        catch (PatternSyntaxException ignore) {
        }
      }

      for (String pattern : patterns) {
        try {
          result.add(Pattern.compile(pattern));
        }
        catch (PatternSyntaxException ignore) {
        }
      }
      return result;
    }

  }
}
