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

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

public class MavenProjectProblem implements Serializable {
  public enum ProblemType {
    SYNTAX, STRUCTURE, DEPENDENCY, PARENT, SETTINGS_OR_PROFILES
  }

  private final String myPath;
  private final String myDescription;
  private final ProblemType myType;

  public static MavenProjectProblem createStructureProblem(String path, String description) {
    return createProblem(path, description, MavenProjectProblem.ProblemType.STRUCTURE);
  }

  public static MavenProjectProblem createSyntaxProblem(String path, MavenProjectProblem.ProblemType type) {
    return createProblem(path, MessageFormat.format("''{0}'' has syntax errors", new File(path).getName()) , type);
  }

  public static MavenProjectProblem createProblem(String path, String description, MavenProjectProblem.ProblemType type) {
    return new MavenProjectProblem(path, description, type);
  }

  public static Collection<MavenProjectProblem> createProblemsList() {
    return createProblemsList(Collections.<MavenProjectProblem>emptySet());
  }

  public static Collection<MavenProjectProblem> createProblemsList(Collection<MavenProjectProblem> copyThis) {
    return new LinkedHashSet<MavenProjectProblem>(copyThis);
  }

  public MavenProjectProblem(String path, String description, ProblemType type) {
    myPath = path;
    myDescription = description;
    myType = type;
  }

  public String getPath() {
    return myPath;
  }

  public String getDescription() {
    return myDescription;
  }

  public ProblemType getType() {
    return myType;
  }

  @Override
  public String toString() {
    return myType + ":" + myDescription + ":" + myPath;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenProjectProblem that = (MavenProjectProblem)o;

    if (!myDescription.equals(that.myDescription)) return false;
    if (myType != that.myType) return false;
    if (!myPath.equals(that.myPath)) return false;

    return true;
  }

  public int hashCode() {
    int result = myPath.hashCode();
    result = 31 * result + myDescription.hashCode();
    result = 31 * result + myType.hashCode();
    return result;
  }
}
