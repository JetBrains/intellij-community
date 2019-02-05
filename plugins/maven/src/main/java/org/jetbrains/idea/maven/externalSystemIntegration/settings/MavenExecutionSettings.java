// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.settings;

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class MavenExecutionSettings extends ExternalSystemExecutionSettings {

  @NotNull private final File myMavenHome;
  @NotNull private final String myMavenVersion;
  @NotNull private final String myJavaHome;
  @NotNull private final VirtualFile myPomFile;

  public MavenExecutionSettings(@NotNull File mavenHome,
                                @NotNull String mavenVersion,
                                @NotNull String javaHome,
                                @NotNull VirtualFile pomFile) {
    myMavenHome = mavenHome;
    myMavenVersion = mavenVersion;
    myJavaHome = javaHome;
    myPomFile = pomFile;
  }

  @NotNull
  public File getMavenHome() {
    return myMavenHome;
  }

  @NotNull
  public String getMavenVersion() {
    return myMavenVersion;
  }


  @NotNull
  public String getJavaHome() {
    return myJavaHome;
  }

  public VirtualFile getPomFile() {
    return myPomFile;
  }
}
