// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.settings;


import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.File;

public class MavenProjectSettings extends ExternalProjectSettings {

  @Nullable private String myMavenHome;
  @Nullable private String myMavenJVM = ExternalSystemJdkUtil.USE_PROJECT_JDK;
  @Nullable private Integer myThreadCount;

  @Nullable private File myMavenSettingsFile;
  @Nullable private File myMavenLocalRepo;

  @NotNull
  @Override
  public ExternalProjectSettings clone() {
    MavenProjectSettings result = new MavenProjectSettings();
    copyTo(result);
    result.myMavenHome = myMavenHome;
    result.myMavenJVM = myMavenJVM;
    result.myThreadCount = myThreadCount;
    result.myMavenSettingsFile = myMavenSettingsFile;
    result.myMavenLocalRepo = myMavenLocalRepo;
    return result;
  }

  @Nullable
  public String getMavenHome() {
    return myMavenHome;
  }

  public void setMavenHome(@Nullable String mavenHome) {
    myMavenHome = mavenHome;
  }

  @Nullable
  public String getMavenJVM() {
    return myMavenJVM;
  }

  public void setMavenJVM(@Nullable String mavenJVM) {
    myMavenJVM = mavenJVM;
  }

  @Nullable
  public Integer getThreadCount() {
    return myThreadCount;
  }

  public void setThreadCount(@Nullable Integer threadCount) {
    myThreadCount = threadCount;
  }

  @Nullable
  public File getMavenSettingsFile() {
    return myMavenSettingsFile;
  }

  public void setMavenSettingsFile(@Nullable File mavenSettingsFile) {
    myMavenSettingsFile = mavenSettingsFile;
  }

  @Nullable
  public File getMavenLocalRepo() {
    return myMavenLocalRepo;
  }

  public void setMavenLocalRepo(@Nullable File mavenLocalRepo) {
    myMavenLocalRepo = mavenLocalRepo;
  }

  @NotNull
  public static MavenProjectSettings getInitial() {
    MavenProjectSettings result = new MavenProjectSettings();
    result.setMavenHome(MavenServerManager.BUNDLED_MAVEN_3);
    return result;
  }
}
