// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;

public class MavenDistribution {

  private final File myMavenHome;
  private final String myName;

  public MavenDistribution(@NotNull File mavenHome, String name) {
    myMavenHome = mavenHome;
    myName = name;
  }

  @NotNull
  public File getMavenHome() {
    return myMavenHome;
  }

  public boolean isValid() {
    return getVersion() != null;
  }

  public String getName(){
    return myName;
  }

  public String getVersion() {
    return MavenUtil.getMavenVersion(myMavenHome);
  }

  @Override
  public String toString() {
    return myName +  "(" + myMavenHome + ")";
  }
}
