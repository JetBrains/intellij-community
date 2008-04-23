package org.jetbrains.idea.maven;

import java.io.File;
import java.io.IOException;

public class SnapshotDependenciesImportingTest extends ImportingTestCase {
  private File remoteRepoDir;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    remoteRepoDir = new File(myDir, "remote");
    remoteRepoDir.mkdirs();
  }

  public void testSnapshotVersionDependencyToModule() throws Exception {
    performTestWithDependencyVersion("1-SNAPSHOT");
  }

  public void testSnapshotRangeDependencyToModule() throws Exception {
    performTestWithDependencyVersion("SNAPSHOT");
  }

  private void performTestWithDependencyVersion(String version) throws Exception {
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
                          "<version>1</version>" +

                          repositoriesSection() +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>" + version + "</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>" + version + "</version>" +

                          distributionManagementSection());

    importProject();
    assertModules("project", "m1", "m2");
    assertModuleModuleDeps("m1", "m2");

    // in order to force maven to resolve dependency into remote one we have to
    // clean up local repository.
    deploy("m2");
    removeFromLocalRepository("test");

    importProject();

    assertModules("project", "m1", "m2");
    assertModuleModuleDeps("m1", "m2");
  }

  public void testNamingLibraryTheSameWayRegardlessAvailableSnapshotVersion() throws Exception {
    deployArtifact("test", "foo", "1-SNAPSHOT");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  repositoriesSection() +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>foo</artifactId>" +
                  "    <version>1-SNAPSHOT</version>" +
                  "  </dependency>" +
                  "</dependencies>");
    assertModuleLibDeps("project", "test:foo:1-SNAPSHOT");

    removeFromLocalRepository("test");
    
    importProject();
    assertModuleLibDeps("project", "test:foo:1-SNAPSHOT");
  }

  private void deployArtifact(String groupId, String artifactId, String version) throws IOException {
    String moduleName = "___" + artifactId;
    createModulePom(moduleName,
                    "<groupId>" + groupId + "</groupId>" +
                    "<artifactId>" + artifactId + "</artifactId>" +
                    "<version>" + version + "</version>" +

                    distributionManagementSection());

    deploy(moduleName);
  }

  private void deploy(String modulePath) {
    executeGoal(modulePath, "deploy");
  }

  private String repositoriesSection() {
    return "<repositories>" +
           "  <repository>" +
           "    <id>internal</id>" +
           "    <url>file://" + remoteRepoDir.getPath() + "</url>" +
           "  </repository>" +
           "</repositories>";
  }

  private String distributionManagementSection() {
     return "<distributionManagement>" +
            "  <repository>" +
            "    <id>internal</id>" +
            "    <url>file://" + remoteRepoDir.getPath() + "</url>" +
            "  </repository>" +
            "</distributionManagement>";
  }
}