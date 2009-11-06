/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.Serializable;

public class MavenProjectProblem implements Serializable {
  public enum ProblemType {
    SYNTAX, STRUCTURE, DEPENDENCY, PARENT, SETTINGS_OR_PROFILES;
  }

  private String myUrl;
  private String myDescription;
  private ProblemType myType;

  protected MavenProjectProblem() {
  }

  public MavenProjectProblem(VirtualFile file, String description, ProblemType type) {
    myUrl = file.getUrl();
    myDescription = description;
    myType = type;
  }

  public String getUrl() {
    return myUrl;
  }

  public String getDescription() {
    return myDescription;
  }

  public ProblemType getType() {
    return myType;
  }

  public VirtualFile findFile() {
    return VirtualFileManager.getInstance().findFileByUrl(myUrl);
  }

  @Override
  public String toString() {
    return myType + ":" + myDescription + ":" + myUrl;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenProjectProblem that = (MavenProjectProblem)o;

    if (!myDescription.equals(that.myDescription)) return false;
    if (myType != that.myType) return false;
    if (!myUrl.equals(that.myUrl)) return false;

    return true;
  }

  public int hashCode() {
    int result = myUrl.hashCode();
    result = 31 * result + myDescription.hashCode();
    result = 31 * result + myType.hashCode();
    return result;
  }
}
