// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compatibility;

import com.intellij.openapi.project.Project;
import org.apache.maven.wrapper.*;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.File;
import java.net.URI;

public class MavenWrapperTestFixture {
  private final static String DISTRIBUTION_URL_PATTERN =
    "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip";
  private final Project myProject;
  private final String myMavenVersion;


  public MavenWrapperTestFixture(Project project, String mavenVersion) {
    myProject = project;

    myMavenVersion = mavenVersion;
  }

  public File getMavenHome() throws Exception {
    Downloader mavenDownloader = new DefaultDownloader("Intellij IDEA", "Integration compatibility tests");
    PathAssembler assembler = new PathAssembler(new File(System.getProperty("user.home"), ".m2"));
    Installer installer = new Installer(mavenDownloader, assembler);
    WrapperConfiguration configuration = new WrapperConfiguration();
    configuration.setAlwaysDownload(false);
    configuration.setDistribution(URI.create(DISTRIBUTION_URL_PATTERN.replace("$version", myMavenVersion)));
    configuration.setDistributionBase(PathAssembler.MAVEN_USER_HOME_STRING);
    configuration.setZipBase(PathAssembler.MAVEN_USER_HOME_STRING);
    return installer.createDist(configuration);
  }

  public void setUp() throws Exception {
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().generalSettings.setMavenHome(getMavenHome().getAbsolutePath());
  }

  public void tearDown() throws Exception {
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().generalSettings.setMavenHome(MavenServerManager.BUNDLED_MAVEN_3);
  }
}
