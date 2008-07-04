package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.List;

public class MavenProjectIndicesManagerTest extends MavenImportingTestCase {
  private MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(myDir, myProject);
    myIndicesFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myIndicesFixture.tearDown();
    super.tearDown();
  }

  public void testAutomaticallyAddingAndUpdatingLocalRepository() throws Exception {
    List<MavenIndex> indices = myIndicesFixture.getIndicesManager().getIndices();

    assertEquals(1, indices.size());

    assertEquals(MavenIndex.Kind.LOCAL, indices.get(0).getKind());
    assertTrue(indices.get(0).getRepositoryPathOrUrl().endsWith("local1"));
    assertTrue(myIndicesFixture.getIndicesManager().hasVersion("junit", "junit", "4.0"));
  }

  public void testAutomaticallyRemoteRepositoriesOnProjectUpdate() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    List<MavenIndex> indices = myIndicesFixture.getIndicesManager().getIndices();
    assertEquals(2, indices.size());

    assertTrue(indices.get(0).getRepositoryPathOrUrl().endsWith("local1"));
    assertEquals("http://repo1.maven.org/maven2", indices.get(1).getRepositoryPathOrUrl());
  }

  public void testUpdatingIndicesOnResolution() throws Exception {
    removeFromLocalRepository("junit/junit/4.0");
    myIndicesFixture.getIndicesManager().scheduleUpdate(myIndicesFixture.getIndicesManager().getIndices());

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

    assertFalse(myIndicesFixture.getIndicesManager().hasVersion("junit", "junit", "4.0"));

    resolveProject();

    assertTrue(myIndicesFixture.getIndicesManager().hasVersion("junit", "junit", "4.0"));
  }
}
