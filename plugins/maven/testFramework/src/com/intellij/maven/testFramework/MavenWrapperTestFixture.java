// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import org.apache.maven.wrapper.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class MavenWrapperTestFixture {
  private final static String DISTRIBUTION_URL_PATTERN =
    "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version$/apache-maven-$version$-bin.zip";

  private final static String SNAPSHOT_METADATA_URL_PATTERN =
    "https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/$version$/maven-metadata.xml";

  private final static String SNAPSHOT_URL_PATTERN =
    "https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/$version$/apache-maven-$versionWithoutSnapshot$-$timestamp$-$build$-bin.zip";

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
    configuration.setDistribution(createURI());
    configuration.setDistributionBase(PathAssembler.MAVEN_USER_HOME_STRING);
    configuration.setZipBase(PathAssembler.MAVEN_USER_HOME_STRING);
    return installer.createDist(configuration);
  }

  @NotNull
  protected URI createURI() throws Exception {
    if (myMavenVersion.contains("SNAPSHOT")) {
      return createSnapshotUri();
    }
    return URI.create(DISTRIBUTION_URL_PATTERN.replace("$version$", myMavenVersion));
  }

  private URI createSnapshotUri() throws Exception {
    URI metadataUri = URI.create(SNAPSHOT_METADATA_URL_PATTERN.replace("$version$", myMavenVersion));
    List<Element> timestampAndBuild = JDOMUtil.load(metadataUri.toURL()).getChildren().stream()
      .filter(e -> "versioning".equals(e.getName()))
      .flatMap(e -> e.getChildren().stream())
      .filter(e -> "snapshot".equals(e.getName()))
      .flatMap(e -> e.getChildren().stream())
      .collect(Collectors.toList());
    String timestamp = null;
    String build = null;
    for(Element e: timestampAndBuild){
      if("timestamp".equals(e.getName())) {
        timestamp = e.getValue();
      }
      if("buildNumber".equals(e.getName())) {
        build = e.getValue();
      }
    }

    if(build == null || timestamp == null){
      throw new Exception("cannot find last version for " + myMavenVersion);
    }
    String versionWithoutSnapshot = myMavenVersion.replace("-SNAPSHOT", "");
    return URI.create(SNAPSHOT_URL_PATTERN
                        .replace("$version$", myMavenVersion)
                        .replace("$versionWithoutSnapshot$", versionWithoutSnapshot)
                        .replace("$timestamp$", timestamp)
                        .replace("$build$", build)
    );
  }

  public void setUp() throws Exception {
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().generalSettings.setMavenHome(getMavenHome().getAbsolutePath());
  }

  public void tearDown() throws Exception {
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().generalSettings.setMavenHome(MavenServerManager.BUNDLED_MAVEN_3);
  }
}
