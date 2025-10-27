// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.platform.testFramework.io.ExternalResourcesChecker;
import org.apache.maven.wrapper.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.BundledMaven3;
import org.jetbrains.idea.maven.project.MavenInSpecificPath;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenWrapperTestFixture {
  private static final String DISTRIBUTION_URL_PATTERN =
    "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version$/apache-maven-$version$-bin.zip";

  private static final String SNAPSHOT_METADATA_URL_PATTERN =
    "https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/$version$/maven-metadata.xml";

  private static final String SNAPSHOT_URL_PATTERN =
    "https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/$version$/apache-maven-$versionWithoutSnapshot$-$timestamp$-$build$-bin.zip";

  private static final String MAVEN_4_URL_PATTERN =
    "https://dlcdn.apache.org/maven/maven-4/$version$/binaries/apache-maven-$version$-bin.zip";
  private final Project myProject;
  private final String myMavenVersion;

  private final Pattern SNAPSHOT_REGEX = Pattern.compile("(?<versionWithoutSnapshot>.*)-(?<timestamp>\\d{8}\\.\\d{6})-(?<build>\\d+)");


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

    try {
      return installer.createDist(configuration);
    }
    catch (IOException e) {
      ExternalResourcesChecker.reportUnavailability("Maven Wrapper", e);

      throw new IllegalStateException(); // should never happen
    }
  }

  protected @NotNull URI createURI() throws Exception {
    if (myMavenVersion.contains("4.0.0.")) {
      return URI.create(MAVEN_4_URL_PATTERN.replace("$version$", myMavenVersion));
    }
    if (myMavenVersion.contains("SNAPSHOT")) {
      return createSnapshotUri();
    }
    var matcher = SNAPSHOT_REGEX.matcher(myMavenVersion);

    if (matcher.matches()) {
      return createTimestampedSnapshotUri(matcher);
    }
    return URI.create(DISTRIBUTION_URL_PATTERN.replace("$version$", myMavenVersion));
  }

  private URI createTimestampedSnapshotUri(Matcher matcher) throws Exception {
    if (!matcher.matches()) throw new IllegalArgumentException("matcher is not applicable");
    String versionWithoutSnapshot = matcher.group("versionWithoutSnapshot");
    String timestamp = matcher.group("timestamp");
    String build = matcher.group("build");
    String versionWithSnapshot = versionWithoutSnapshot + "-SNAPSHOT";

    return URI.create(SNAPSHOT_URL_PATTERN
                        .replace("$version$", versionWithSnapshot)
                        .replace("$versionWithoutSnapshot$", versionWithoutSnapshot)
                        .replace("$timestamp$", timestamp)
                        .replace("$build$", build));
  }

  private URI createSnapshotUri() throws Exception {
    URI metadataUri = URI.create(SNAPSHOT_METADATA_URL_PATTERN.replace("$version$", myMavenVersion));
    List<Element> timestampAndBuild = JDOMUtil.load(metadataUri.toURL()).getChildren().stream()
      .filter(e -> "versioning".equals(e.getName()))
      .flatMap(e -> e.getChildren().stream())
      .filter(e -> "snapshot".equals(e.getName()))
      .flatMap(e -> e.getChildren().stream())
      .toList();
    String timestamp = null;
    String build = null;
    for (Element e : timestampAndBuild) {
      if ("timestamp".equals(e.getName())) {
        timestamp = e.getValue();
      }
      if ("buildNumber".equals(e.getName())) {
        build = e.getValue();
      }
    }

    if (build == null || timestamp == null) {
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
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().getGeneralSettings()
      .setMavenHomeType(new MavenInSpecificPath(getMavenHome().getAbsolutePath()));
  }

  public void tearDown() throws Exception {
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().getGeneralSettings().setMavenHomeNoFire(BundledMaven3.INSTANCE);
  }
}
