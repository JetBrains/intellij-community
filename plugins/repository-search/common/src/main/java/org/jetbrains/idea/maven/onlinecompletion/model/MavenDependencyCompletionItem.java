// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;

@ApiStatus.Experimental
public class MavenDependencyCompletionItem extends MavenId {

  private final Type myType;

  private final String packaging;
  private final String classifier;

  public enum Type {
    REMOTE(10), LOCAL(20), PROJECT(100), CACHED_ERROR(-1);

    private final int myWeight;

    Type(int weight) {myWeight = weight;}

    public int getWeight() {
      return myWeight;
    }
  }


  public MavenDependencyCompletionItem(@Nullable String groupId,
                                       @Nullable String artifactId,
                                       @Nullable String version,
                                       @Nullable String packaging,
                                       @Nullable String classifier,
                                       @Nullable Type type) {
    super(groupId, artifactId, version);
    myType = type;
    this.packaging = packaging;
    this.classifier = classifier;
  }

  public MavenDependencyCompletionItem(@Nullable String groupId,
                                       @Nullable String artifactId,
                                       @Nullable String version,
                                       @Nullable Type type) {
    this(groupId, artifactId, version, null, null, type);
  }

  public MavenDependencyCompletionItem(@Nullable String groupId,
                                       @Nullable String artifactId,
                                       @Nullable String version) {
    this(groupId, artifactId, version, null, null, null);
  }

  public MavenDependencyCompletionItem(@Nullable String coord, @Nullable Type type) {
    super(coord);
    packaging = null;
    classifier = null;
    myType = type;
  }

  public MavenDependencyCompletionItem(@Nullable String coord) {
    this(coord, null);
  }

  public @Nullable Type getType() {
    return myType;
  }


  public String getPackaging() {
    return packaging;
  }

  public String getClassifier() {
    return classifier;
  }

}
