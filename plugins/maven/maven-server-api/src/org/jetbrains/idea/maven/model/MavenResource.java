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

import java.io.Serializable;
import java.util.List;

public class MavenResource implements Serializable {
  private final String myDirectory;
  private final boolean myFiltered;
  private final String myTargetPath;
  private final List<String> myIncludes;
  private final List<String> myExcludes;

  public MavenResource(String directory, boolean filtered, String targetPath, List<String> includes, List<String> excludes) {
    myDirectory = directory;
    myFiltered = filtered;
    myTargetPath = targetPath;
    myIncludes = includes;
    myExcludes = excludes;
  }

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
    if (myDirectory != null ? !myDirectory.equals(that.myDirectory) : that.myDirectory != null) return false;
    if (myExcludes != null ? !myExcludes.equals(that.myExcludes) : that.myExcludes != null) return false;
    if (myIncludes != null ? !myIncludes.equals(that.myIncludes) : that.myIncludes != null) return false;
    if (myTargetPath != null ? !myTargetPath.equals(that.myTargetPath) : that.myTargetPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDirectory != null ? myDirectory.hashCode() : 0;
    result = 31 * result + (myFiltered ? 1 : 0);
    result = 31 * result + (myTargetPath != null ? myTargetPath.hashCode() : 0);
    result = 31 * result + (myIncludes != null ? myIncludes.hashCode() : 0);
    result = 31 * result + (myExcludes != null ? myExcludes.hashCode() : 0);
    return result;
  }
}
