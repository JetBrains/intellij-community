// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven;

import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.compatibility.MavenWrapperTestFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public abstract class MavenMultiVersionImportingTestCase extends MavenImportingTestCase {

  @Parameterized.Parameters(name = "with Maven-{0}")
  public static List<String[]> getMavenVersions() {
    return Arrays.asList(
      new String[]{"3.8.1"},
      new String[]{"3.6.3"},
      new String[]{"3.6.1"},
      new String[]{"3.5.3"},
      new String[]{"3.3.9"},
      new String[]{"3.0.1"},
      new String[]{"3.0"}
    );
  }

  @NotNull
  protected MavenWrapperTestFixture myWrapperTestFixture;

  @Parameterized.Parameter
  public String myMavenVersion;

  protected void assumeVersionMoreThan(String version) {
    Assume.assumeTrue("Version should be more than " + version, VersionComparatorUtil.compare(myMavenVersion, version) > 0);
  }

  protected void assumeVersionLessThan(String version) {
    Assume.assumeTrue("Version should be less than " + version, VersionComparatorUtil.compare(myMavenVersion, version) > 0);
  }

  protected void assumeVersionNot(String version) {
    Assume.assumeTrue("Version " + version + " skipped", VersionComparatorUtil.compare(myMavenVersion, version) != 0);
  }

  @Before
  public void before() throws Exception {
    myWrapperTestFixture = new MavenWrapperTestFixture(myProject, myMavenVersion);
    myWrapperTestFixture.setUp();
  }
}
