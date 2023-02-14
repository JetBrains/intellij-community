/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MavenResource implements Serializable {
  @NotNull
  private final String myDirectory;
  private final boolean myFiltered;
  private final String myTargetPath;
  private final List<String> myIncludes;
  private final List<String> myExcludes;

  public MavenResource(@NotNull String directory, boolean filtered, String targetPath, List<String> includes, List<String> excludes) {
    myDirectory = directory;
    myFiltered = filtered;
    myTargetPath = targetPath;
    myIncludes = includes == null ? Collections.emptyList() : new ArrayList<String>(includes);
    myExcludes = excludes == null ? Collections.emptyList() : new ArrayList<String>(excludes);
  }

  @NotNull
  public String getDirectory() {
    return myDirectory;
  }

  public boolean isFiltered() {
    return myFiltered;
  }

  public String getTargetPath() {
    return myTargetPath;
  }

  public List<String> getIncludes() {
    return myIncludes;
  }

  public List<String> getExcludes() {
    return myExcludes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenResource that = (MavenResource)o;

    if (myFiltered != that.myFiltered) return false;
    if (!Objects.equals(myDirectory, that.myDirectory)) return false;
    if (!myExcludes.equals(that.myExcludes)) return false;
    if (!myIncludes.equals(that.myIncludes)) return false;
    if (!Objects.equals(myTargetPath, that.myTargetPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDirectory.hashCode();
    result = 31 * result + (myFiltered ? 1 : 0);
    result = 31 * result + (myTargetPath != null ? myTargetPath.hashCode() : 0);
    result = 31 * result + myIncludes.hashCode();
    result = 31 * result + myExcludes.hashCode();
    return result;
  }
}
