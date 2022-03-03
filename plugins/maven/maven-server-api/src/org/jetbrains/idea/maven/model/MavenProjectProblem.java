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

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

public class MavenProjectProblem implements Serializable {
  //todo: this enum values are write-only now
  public enum ProblemType {
    SYNTAX, STRUCTURE, DEPENDENCY, PARENT, SETTINGS_OR_PROFILES, REPOSITORY_BLOCKED, REPOSITORY
  }

  private final boolean myRecoverable;
  private final String myPath;
  private final String myDescription;
  private final ProblemType myType;

  public static MavenProjectProblem createStructureProblem(String path, String description, boolean recoverable) {
    return createProblem(path, description, MavenProjectProblem.ProblemType.STRUCTURE, recoverable);
  }

  public static MavenProjectProblem createStructureProblem(String path, String description) {
    return createProblem(path, description, MavenProjectProblem.ProblemType.STRUCTURE, false);
  }

  public static MavenProjectProblem createSyntaxProblem(String path, MavenProjectProblem.ProblemType type) {
    return createProblem(path, MessageFormat.format("''{0}'' has syntax errors", new File(path).getName()), type, false);
  }

  public static MavenProjectProblem createProblem(String path,
                                                  String description,
                                                  MavenProjectProblem.ProblemType type,
                                                  boolean recoverable) {
    return new MavenProjectProblem(path, description, type, recoverable);
  }

  public static Collection<MavenProjectProblem> createProblemsList() {
    return createProblemsList(Collections.<MavenProjectProblem>emptySet());
  }

  public static Collection<MavenProjectProblem> createProblemsList(Collection<? extends MavenProjectProblem> copyThis) {
    return new LinkedHashSet<MavenProjectProblem>(copyThis);
  }

  public MavenProjectProblem(String path, String description, ProblemType type, boolean recoverable) {
    myPath = path;
    myDescription = description;
    myType = type;
    myRecoverable = recoverable;
  }

  public String getPath() {
    return myPath;
  }

  public boolean isRecoverable() {
    return myRecoverable;
  }

  public @Nullable String getDescription() {
    return myDescription;
  }

  public ProblemType getType() {
    return myType;
  }

  @Override
  public String toString() {
    return myType + ":" + myDescription + ":" + myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenProjectProblem that = (MavenProjectProblem)o;

    if (myDescription != null ? !myDescription.equals(that.myDescription) : that.myDescription != null) return false;
    if (myType != that.myType) return false;
    if (myPath != null ? !myPath.equals(that.myPath) : that.myPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPath != null ? myPath.hashCode() : 0;
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
