// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class MavenProfile extends MavenModelBase implements Serializable {
  private final String myId;
  private final String mySource;
  private MavenProfileActivation myActivation;
  private final MavenBuildBase myBuild;

  public MavenProfile(String id, String source) {
    myId = id;
    mySource = source;
    myBuild = new MavenBuildBase();
  }

  protected MavenProfile(@NotNull MavenProfile other) {
    super(other);
    myId = other.myId;
    mySource = other.mySource;
    myActivation = other.myActivation;
    myBuild = other.myBuild.copy();
  }

  @Override
  public MavenProfile copy() {
    return new MavenProfile(this);
  }

  public @NotNull String getId() {
    return myId;
  }

  public String getSource() {
    return mySource;
  }

  public void setActivation(MavenProfileActivation activation) {
    myActivation = activation;
  }

  public MavenProfileActivation getActivation() {
    return myActivation;
  }

  public MavenBuildBase getBuild() {
    return myBuild;
  }
}
