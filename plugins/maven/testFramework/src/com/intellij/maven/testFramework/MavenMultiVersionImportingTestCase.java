// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public abstract class MavenMultiVersionImportingTestCase extends MavenImportingTestCase {

  public static final String[] MAVEN_VERSIONS = new String[]{"bundled"};

  @Parameterized.Parameters(name = "with Maven-{0}")
  public static List<String[]> getMavenVersions() {
    String mavenVersionsString = System.getProperty("maven.versions.to.run");
    String[] mavenVersionsToRun = MAVEN_VERSIONS;
    if (mavenVersionsString != null && !mavenVersionsString.isEmpty()) {
      mavenVersionsToRun = mavenVersionsString.split(",");
    }
    return ContainerUtil.map(mavenVersionsToRun, it -> new String[]{it});
  }

  @Nullable
  protected MavenWrapperTestFixture myWrapperTestFixture;

  @Parameterized.Parameter(0)
  public String myMavenVersion;

  protected void assumeVersionMoreThan(String version) {
    Assume.assumeTrue("Version should be more than " + version, VersionComparatorUtil.compare(myMavenVersion, version) > 0);
  }

  protected void assumeVersionLessThan(String version) {
    Assume.assumeTrue("Version should be less than " + version, VersionComparatorUtil.compare(myMavenVersion, version) < 0);
  }

  protected void assumeVersionNot(String version) {
    Assume.assumeTrue("Version " + version + " skipped", VersionComparatorUtil.compare(myMavenVersion, version) != 0);
  }

  @Before
  public void before() throws Exception {
    if ("bundled".equals(myMavenVersion)) {
      return;
    }
    myWrapperTestFixture = new MavenWrapperTestFixture(myProject, myMavenVersion);
    myWrapperTestFixture.setUp();
  }

  @After
  public void after() throws Exception {
    if (myWrapperTestFixture != null) {
      myWrapperTestFixture.tearDown();
    }
  }

  protected LanguageLevel getDefaultLanguageLevel() {
    var version = getActualVersion(myMavenVersion);
    if (VersionComparatorUtil.compare("3.9.0", version) <= 0) {
      return LanguageLevel.JDK_1_7;
    }
    return LanguageLevel.JDK_1_5;
  }

  protected static String getActualVersion(String version) {
    if (version.equals("bundled")) {
      return MavenDistributionsCache.resolveEmbeddedMavenHome().getVersion();
    }
    return version;
  }

  @NotNull
  protected String getDefaultPluginVersion(String pluginId) {
    if (pluginId.equals("org.apache.maven:maven-compiler-plugin")) {
      if (mavenVersionIsOrMoreThan("3.9.0")) {
        return "3.10.1";
      }
      return "3.1";
    }
    throw new IllegalArgumentException(
      "this plugin is not configured yet, consider https://youtrack.jetbrains.com/issue/IDEA-313733/create-matrix-of-plugin-levels-for-different-java-versions");
  }

  protected boolean mavenVersionIsOrMoreThan(String version) {
    return StringUtil.compareVersionNumbers(version, getActualVersion(myMavenVersion)) <= 0;
  }

  protected boolean isMaven4() {
    return StringUtil.compareVersionNumbers(getActualVersion(myMavenVersion), "4.0") >= 0;
  }

  private List<String> defaultResources() {
    return isMaven4() ? List.of("src/main/resources", "src/main/resources-filtered") : List.of("src/main/resources");
  }

  private List<String> defaultTestResources() {
    return isMaven4() ? List.of("src/test/resources", "src/test/resources-filtered") : List.of("src/test/resources");
  }

  protected void assertDefaultResources(String moduleName, String... additionalSources) {
    var expectedList = new ArrayList<String>();
    expectedList.addAll(defaultResources());
    expectedList.addAll(Arrays.asList(additionalSources));
    var expectedSources = ArrayUtil.toStringArray(expectedList);
    assertResources(moduleName, expectedSources);
  }

  protected void assertDefaultTestResources(String moduleName, String... additionalSources) {
    var expectedList = new ArrayList<String>();
    expectedList.addAll(defaultTestResources());
    expectedList.addAll(Arrays.asList(additionalSources));
    var expectedSources = ArrayUtil.toStringArray(expectedList);
    assertTestResources(moduleName, expectedSources);
  }
}
