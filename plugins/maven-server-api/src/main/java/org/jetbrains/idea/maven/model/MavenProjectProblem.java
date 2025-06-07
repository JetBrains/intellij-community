// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Objects;

public class MavenProjectProblem implements Serializable {
  //todo: this enum values are write-only now
  public enum ProblemType {
    SYNTAX, STRUCTURE, DEPENDENCY, PARENT, SETTINGS_OR_PROFILES, REPOSITORY
  }

  private final boolean myIsError;
  private final String myPath;
  private final String myDescription;
  private final ProblemType myType;
  private final Exception st;
  private final @Nullable MavenArtifact myMavenArtifact;

  public static MavenProjectProblem createStructureProblem(String path, String description, boolean isError) {
    return createProblem(path, description, ProblemType.STRUCTURE, isError);
  }

  public static MavenProjectProblem createStructureProblem(String path, String description) {
    return createProblem(path, description, ProblemType.STRUCTURE, true);
  }

  public static MavenProjectProblem createSyntaxProblem(String path, ProblemType type) {
    return createProblem(path, MessageFormat.format("''{0}'' has syntax errors", new File(path).getName()), type, true);
  }

  public static MavenProjectProblem createProblem(String path,
                                                  String description,
                                                  ProblemType type,
                                                  boolean isError) {
    return new MavenProjectProblem(path, description, type, isError);
  }

  public static MavenProjectProblem createRepositoryProblem(String path,
                                                            String description,
                                                            boolean isError,
                                                            MavenArtifact mavenArtifact) {
    return new MavenProjectProblem(path, description, ProblemType.REPOSITORY, isError, mavenArtifact);
  }

  public static MavenProjectProblem createUnresolvedArtifactProblem(String path,
                                                                    String description,
                                                                    boolean isError,
                                                                    MavenArtifact mavenArtifact) {
    return new MavenProjectProblem(path, description, ProblemType.DEPENDENCY, isError, mavenArtifact);
  }

  public MavenProjectProblem(String path, String description, ProblemType type, boolean isError) {
    this(path, description, type, isError, null);
  }

  public MavenProjectProblem(String path, String description, ProblemType type, boolean isError, MavenArtifact mavenArtifact) {
    myPath = path;
    myDescription = description;
    myType = type;
    myIsError = isError;
    myMavenArtifact = mavenArtifact;
    st = new Exception();
  }

  public String getPath() {
    return myPath;
  }

  public boolean isError() {
    return myIsError;
  }

  public @Nullable String getDescription() {
    return myDescription;
  }

  public ProblemType getType() {
    return myType;
  }

  public @Nullable MavenArtifact getMavenArtifact() {
    return myMavenArtifact;
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

    if (!Objects.equals(myDescription, that.myDescription)) return false;
    if (myType != that.myType) return false;
    if (!Objects.equals(myPath, that.myPath)) return false;

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
