package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.MavenImportingTestCase;

public class MavenIndicesManagerTest extends MavenImportingTestCase {
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

  public void testUpdatingIndicesOnResolution() throws Exception {
    removeFromLocalRepository("junit/junit/4.0");
    myIndicesFixture.getIndicesManager().scheduleUpdateAll(myProject);

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

    assertFalse(myIndicesFixture.getIndicesManager().hasVersion(myProject, "junit", "junit", "4.0"));

    resolveProject();

    assertTrue(myIndicesFixture.getIndicesManager().hasVersion(myProject, "junit", "junit", "4.0"));
  }
}
