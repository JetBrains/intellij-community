package org.jetbrains.idea.maven;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import org.jetbrains.idea.maven.indices.MavenCustomRepositoryHelper;

import java.io.File;

public class MiscImportingTest extends MavenImportingTestCase {
  private int count;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        count++;
      }
    });
  }

  public void testImportingFiresRootChangesOnlyOnce() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, count);
  }

  public void testResolvingFiresRootChangesOnlyOnce() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, count);

    resolveProject();
    assertEquals(2, count);
  }

  public void testFacetsDoNotFireRootsChanges() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>war</packaging>");

    assertEquals(1, count);

    resolveProject();
    assertEquals(2, count);
  }

  public void testDoNotRecreateModulesBeforeResolution() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Module m = getModule("project");
    resolveProject();

    assertSame(m, getModule("project"));
  }

  public void testTakingProxySettingsIntoAccount() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
    setRepositoryPath(helper.getTestDataPath("local1"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    removeFromLocalRepository("junit");
    resolveProject();

    File jarFile = new File(getRepositoryFile(), "junit/junit/4.0/junit-4.0.jar");
    assertTrue(jarFile.exists());

    setCustomSettingsFile("<settings>" +
                          "  <proxies>" +
                          "   <proxy>" +
                          "      <id>my</id>" +
                          "      <active>true</active>" +
                          "      <protocol>http</protocol>" +
                          "      <username>coox</username>" +
                          "      <password>invalid</password>" + // valid password is 'test'
                          "      <host>is.intellij.net</host>" +
                          "      <port>3128</port>" +
                          "    </proxy>" +
                          "  </proxies>" +
                          "</settings>");

    removeFromLocalRepository("junit");
    assertFalse(jarFile.exists());

    try {
      resolveProject();
    }
    finally {
      // LightweightHttpWagon does not clear settings if they were not set before a proxy was configured.
      System.clearProperty("http.proxyHost");
      System.clearProperty("http.proxyPort");
    }
    assertFalse(jarFile.exists());

    setCustomSettingsFile("<settings>" +
                          "</settings>");

    resolveProject();
    assertTrue(jarFile.exists());
  }
}
