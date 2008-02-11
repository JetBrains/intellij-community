package org.jetbrains.idea.maven;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;

public class ReimportingTest extends ImportingTestCase {
  @Override
  protected void tearDown() throws Exception {
    Messages.setTestDialog(TestDialog.DEFAULT);
    super.tearDown();
  }

  public void testAddingNewModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    importProject();
    assertModules("project", "m1", "m2");
  }

  public void testRemovingObsoleteModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    configMessagesForNoAnswer();
    importProject();
    assertModules("project", "m1", "m2");

    configMessagesForYesAnswer();
    importProject();
    assertModules("project", "m1");
  }

  private void configMessagesForNoAnswer() {
    Messages.setTestDialog(new TestDialog() {
      public int show(String message) {
        return 1;
      }
    });
  }

  private void configMessagesForYesAnswer() {
    Messages.setTestDialog(TestDialog.OK);
  }
}
