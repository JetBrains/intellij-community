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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.maven.compiler.MavenPatternFileFilter;

import java.io.File;
import java.io.FileFilter;

/**
 * @author nik
 */
public class MavenResourceFileFilter implements FileFilter {
  private File myRoot;
  private String myRelativeDirectoryPath;
  private MavenPatternFileFilter myMavenPatternFileFilter;

  public MavenResourceFileFilter(@NotNull File rootFile, @NotNull FilePattern filePattern) {
    this(rootFile, filePattern, null);
  }

  public MavenResourceFileFilter(@NotNull File rootFile, @NotNull FilePattern filePattern, @Nullable String relativeDirectoryPath) {
    myMavenPatternFileFilter = new MavenPatternFileFilter(filePattern.includes, filePattern.excludes);
    myRoot = rootFile;
    myRelativeDirectoryPath = relativeDirectoryPath;
  }

  @Override
  public boolean accept(@NotNull File file) {
    String relativeFilePath = myRelativeDirectoryPath != null ?
                              myRelativeDirectoryPath + File.separator + file.getName() : FileUtil.getRelativePath(myRoot, file);
    return relativeFilePath != null && myMavenPatternFileFilter.accept(relativeFilePath);
  }
}
