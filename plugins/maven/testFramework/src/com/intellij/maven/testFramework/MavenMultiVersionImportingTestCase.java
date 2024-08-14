// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.RunAll;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public abstract class MavenMultiVersionImportingTestCase extends MavenImportingTestCase {
  @Override
  public boolean runInDispatchThread() { return false; }

  public static final String[] MAVEN_VERSIONS = new String[]{"bundled", "4.0.0-beta-3"};
  @Parameterized.Parameter(0)
  public String myMavenVersion;
  @Nullable
  protected MavenWrapperTestFixture myWrapperTestFixture;

  protected void assumeVersionMoreThan(String version) {
    Assume.assumeTrue("Version should be more than " + version,
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion), getActualVersion(version)) > 0);
  }


  protected void forMaven3(Runnable r) {
    String version = getActualVersion(myMavenVersion);
    if(version.startsWith("3.")) r.run();
  }

  protected void forMaven4(Runnable r) {
    String version = getActualVersion(myMavenVersion);
    if(version.startsWith("4.")) r.run();
  }

  protected void needFixForMaven4() {
    String version = getActualVersion(myMavenVersion);
    Assume.assumeTrue(version.startsWith("3."));
  }

  protected void assumeMaven3() {
    String version = getActualVersion(myMavenVersion);
    Assume.assumeTrue(version.startsWith("3."));
  }

  protected void assumeMaven4() {
    String version = getActualVersion(myMavenVersion);
    Assume.assumeTrue(version.startsWith("4."));
  }

  protected void assumeVersionAtLeast(String version) {
    Assume.assumeTrue("Version should be " + version + " or more",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion), getActualVersion(version)) >= 0);
  }

  protected void assumeVersionLessThan(String version) {
    Assume.assumeTrue("Version should be less than " + version,
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion), getActualVersion(version)) < 0);
  }

  protected void assumeVersionNot(String version) {
    Assume.assumeTrue("Version " + version + " skipped",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion), getActualVersion(version)) != 0);
  }

  protected void assumeVersion(String version) {
    Assume.assumeTrue("Version " + myMavenVersion + " is not " + version + ", therefore skipped",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion), getActualVersion(version)) == 0);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if ("bundled".equals(myMavenVersion)) {
      MavenDistributionsCache.resolveEmbeddedMavenHome();
      return;
    }
    myWrapperTestFixture = new MavenWrapperTestFixture(getProject(), myMavenVersion);
    myWrapperTestFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    new RunAll(
      () -> {
        if (myWrapperTestFixture != null) {
          myWrapperTestFixture.tearDown();
        }
      },
      () -> super.tearDown()).run();
  }

  protected LanguageLevel getDefaultLanguageLevel() {
    var version = getActualVersion(myMavenVersion);
    if (VersionComparatorUtil.compare("3.9.3", version) <= 0) {
      return LanguageLevel.JDK_1_8;
    }
    if (VersionComparatorUtil.compare("3.9.0", version) <= 0) {
      return LanguageLevel.JDK_1_7;
    }
    return LanguageLevel.JDK_1_5;
  }

  @NotNull
  protected String getDefaultPluginVersion(String pluginId) {
    if (pluginId.equals("org.apache.maven:maven-compiler-plugin")) {
      if (mavenVersionIsOrMoreThan("3.9.7")) {
        return "3.13.0";
      }
      if (mavenVersionIsOrMoreThan("3.9.3")) {
        return "3.11.0";
      }
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

  protected String maven4orNull(String value) {
    return isMaven4() ? value : null;
  }

  protected String[] defaultResources() {
    return arrayOfNotNull("src/main/resources", maven4orNull("src/main/resources-filtered"));
  }

  protected String[] defaultTestResources() {
    return arrayOfNotNull("src/test/resources", maven4orNull("src/test/resources-filtered"));
  }

  protected String[] allDefaultResources() {
    return ArrayUtil.mergeArrays(defaultResources(), defaultTestResources());
  }

  protected void assertDefaultResources(String moduleName, String... additionalSources) {
    var expectedSources = ArrayUtil.mergeArrays(defaultResources(), additionalSources);
    assertResources(moduleName, expectedSources);
  }

  protected void assertDefaultTestResources(String moduleName, String... additionalSources) {
    var expectedSources = ArrayUtil.mergeArrays(defaultTestResources(), additionalSources);
    assertTestResources(moduleName, expectedSources);
  }

  protected void assertDefaultResources(String moduleName, @NotNull JpsModuleSourceRootType<?> rootType, String... additionalSources) {
    var expectedSources = ArrayUtil.mergeArrays(defaultResources(), additionalSources);
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), expectedSources);
  }

  protected void assertDefaultTestResources(String moduleName, @NotNull JpsModuleSourceRootType<?> rootType, String... additionalSources) {
    var expectedSources = ArrayUtil.mergeArrays(defaultTestResources(), additionalSources);
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), expectedSources);
  }

  protected String[] arrayOfNotNull(String... values) {
    if (null == values) return ArrayUtil.EMPTY_STRING_ARRAY;
    return Arrays.stream(values).filter(v -> null != v).toArray(String[]::new);
  }

  protected void createStdProjectFolders() {
    createStdProjectFolders("");
  }

  protected void createStdProjectFolders(String subdir) {
    if (!subdir.isEmpty()) subdir += "/";

    var folders = ArrayUtil.mergeArrays(allDefaultResources(),
                                        "src/main/java",
                                        "src/test/java"
    );

    createProjectSubDirs(subdir, folders);
  }

  private void createProjectSubDirs(String subdir, String... relativePaths) {
    for (String path : relativePaths) {
      createProjectSubDir(subdir + path);
    }
  }

  protected void assertRelativeContentRoots(String moduleName, String... expectedRelativeRoots) {
    var expectedRoots = Arrays.stream(expectedRelativeRoots)
      .map(root -> getProjectPath() + ("".equals(root) ? "" : "/" + root))
      .toArray(String[]::new);
    assertContentRoots(moduleName, expectedRoots);
  }

  protected void assertContentRoots(String moduleName, String... expectedRoots) {
    List<String> actual = new ArrayList<>();
    for (ContentEntry e : getContentRoots(moduleName)) {
      actual.add(e.getUrl());
    }
    assertUnorderedPathsAreEqual(actual, ContainerUtil.map(expectedRoots, root -> VfsUtilCore.pathToUrl(root)));
  }

  protected void assertGeneratedSources(String moduleName, String... expectedSources) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    List<ContentFolder> folders = new ArrayList<>();
    for (SourceFolder folder : contentRoot.getSourceFolders(JavaSourceRootType.SOURCE)) {
      JavaSourceRootProperties properties = folder.getJpsElement().getProperties(JavaSourceRootType.SOURCE);
      assertNotNull(properties);
      if (properties.isForGeneratedSources()) {
        folders.add(folder);
      }
    }
    doAssertContentFolders(contentRoot, folders, expectedSources);
  }

  protected void assertSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.SOURCE, expectedSources);
  }

  protected void assertContentRootSources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.SOURCE), expectedSources);
  }

  protected void assertResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.RESOURCE, expectedSources);
  }

  protected void assertContentRootResources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.RESOURCE), expectedSources);
  }

  protected void assertTestSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.TEST_SOURCE, expectedSources);
  }

  protected void assertContentRootTestSources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.TEST_SOURCE), expectedSources);
  }

  protected void assertTestResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.TEST_RESOURCE, expectedSources);
  }

  protected void assertContentRootTestResources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.TEST_RESOURCE), expectedSources);
  }

  protected void assertExcludes(String moduleName, String... expectedExcludes) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, Arrays.asList(contentRoot.getExcludeFolders()), expectedExcludes);
  }

  protected void assertContentRootExcludes(String moduleName, String contentRoot, String... expectedExcudes) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, Arrays.asList(root.getExcludeFolders()), expectedExcudes);
  }

  protected void doAssertContentFolders(String moduleName, @NotNull JpsModuleSourceRootType<?> rootType, String... expected) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), expected);
  }

  private ContentEntry getContentRoot(String moduleName) {
    ContentEntry[] ee = getContentRoots(moduleName);
    List<String> roots = new ArrayList<>();
    for (ContentEntry e : ee) {
      roots.add(e.getUrl());
    }

    String message = "Several content roots found: [" + StringUtil.join(roots, ", ") + "]";
    assertEquals(message, 1, ee.length);

    return ee[0];
  }

  private ContentEntry getContentRoot(String moduleName, String path) {
    ContentEntry[] roots = getContentRoots(moduleName);
    for (ContentEntry e : roots) {
      if (e.getUrl().equals(VfsUtilCore.pathToUrl(path))) return e;
    }
    throw new AssertionError("content root not found in module " + moduleName + ":" +
                             "\nExpected root: " + path +
                             "\nExisting roots:" +
                             "\n" + StringUtil.join(roots, it -> " * " + it.getUrl(), "\n"));
  }

  @Parameterized.Parameters(name = "with Maven-{0}")
  public static List<String[]> getMavenVersions() {
    String mavenVersionsString = System.getProperty("maven.versions.to.run");
    String[] mavenVersionsToRun = MAVEN_VERSIONS;
    if (mavenVersionsString != null && !mavenVersionsString.isEmpty()) {
      mavenVersionsToRun = mavenVersionsString.split(",");
    }
    return ContainerUtil.map(mavenVersionsToRun, it -> new String[]{it});
  }

  protected static String getActualVersion(String version) {
    if (version.equals("bundled")) {
      return MavenDistributionsCache.resolveEmbeddedMavenHome().getVersion();
    }
    return version;
  }

  private static void doAssertContentFolders(ContentEntry e,
                                             final List<? extends ContentFolder> folders,
                                             String... expected) {
    List<String> actual = new ArrayList<>();
    for (ContentFolder f : folders) {
      String rootUrl = e.getUrl();
      String folderUrl = f.getUrl();

      if (folderUrl.startsWith(rootUrl)) {
        int length = rootUrl.length() + 1;
        folderUrl = folderUrl.substring(Math.min(length, folderUrl.length()));
      }

      actual.add(folderUrl);
    }

    assertSameElements("Unexpected list of folders in content root " + e.getUrl(),
                       actual, Arrays.asList(expected));
  }
}
