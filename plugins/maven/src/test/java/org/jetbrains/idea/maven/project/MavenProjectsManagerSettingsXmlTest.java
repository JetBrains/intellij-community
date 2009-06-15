package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.MavenImportingTestCase;

public class MavenProjectsManagerSettingsXmlTest extends MavenImportingTestCase {
  public void testUpdatingProjectsOnSettingsXmlCreationAndDeletion() throws Exception {
    deleteSettingsXml();
    initProjectsManager(true);
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    importProject();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles());

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "  </profile>" +
                      "</profiles>");
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles(), "one");

    deleteSettingsXml();
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles());
  }
}