package org.jetbrains.idea.maven;

import org.jetbrains.idea.maven.project.MavenArtifactDownloader;

import java.io.File;

public class ArtifactsDownloadingTest extends ImportingTestCase {
  public void testSimple() throws Exception {
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

    removeFromLocalRepository("junit/junit/4.0");

    File sources = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-sources.jar");
    File javadoc = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-javadoc.jar");
    
    assertFalse(sources.exists());
    assertFalse(javadoc.exists());

    download();

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());
  }

  private void download() throws Exception {
    MavenArtifactDownloader.download(myProject);
  }
}
