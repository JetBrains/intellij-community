package org.jetbrains.idea.maven;

import org.jetbrains.idea.maven.indices.MavenCustomRepositoryTestFixture;

import java.io.File;

public class ArtifactsDownloadingTest extends MavenImportingTestCase {
  MavenCustomRepositoryTestFixture myRepositoryFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepositoryFixture = new MavenCustomRepositoryTestFixture(myDir, "plugins", "local1");
    myRepositoryFixture.setUp();
    myRepositoryFixture.copy("plugins", "local1");
    setRepositoryPath(myRepositoryFixture.getTestDataPath("local1"));
  }

  public void testJavadocsAndSources() throws Exception {
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

    File sources = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-sources.jar");
    File javadoc = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-javadoc.jar");

    assertFalse(sources.exists());
    assertFalse(javadoc.exists());

    download();

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());
  }

  public void testJavadocsAndSourcesForTestDeps() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <scope>test</scope>" +
                  "  </dependency>" +
                  "</dependencies>");

    File sources = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-sources.jar");
    File javadoc = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-javadoc.jar");

    assertFalse(sources.exists());
    assertFalse(javadoc.exists());

    download();

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());
  }

  public void testDownloadingPlugins() throws Exception {
    // embedder does not release loaded plugins and does not let us remove the temp dir.
    if (ignore()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "      <version>2.4.2</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    File f = new File(getRepositoryPath(), "/org/apache/maven/plugins/maven-surefire-plugin/2.4.2/maven-surefire-plugin-2.4.2.jar");
    assertFalse(f.exists());

    download();

    assertTrue(f.exists());
  }

  public void testDownloadNecessaryBuildExtensionsOnResolve() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <extensions>" +
                  "    <extension>" +
                  "      <groupId>org.apache.maven.wagon</groupId>" +
                  "      <artifactId>wagon</artifactId>" +
                  "      <version>1.0-alpha-6</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    File f = new File(getRepositoryPath(), "/org/apache/maven/wagon/wagon/1.0-alpha-6/wagon-1.0-alpha-6.pom");
    assertFalse(f.exists());

    resolveProject();

    assertTrue(f.exists());
  }

  private void download() throws Exception {
    myMavenProjectsManager.downloadArtifacts();
  }
}
