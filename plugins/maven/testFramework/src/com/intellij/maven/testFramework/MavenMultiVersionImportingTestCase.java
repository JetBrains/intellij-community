// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
}
