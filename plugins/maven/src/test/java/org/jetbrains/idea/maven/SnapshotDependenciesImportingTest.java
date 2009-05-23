package org.jetbrains.idea.maven;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class SnapshotDependenciesImportingTest extends MavenImportingTestCase {
  private File remoteRepoDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // disable local mirrors
    updateSettingsXmlFully("<settings></settings>");
  }

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
    assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT");

    removeFromLocalRepository("test");

    importProject();
    assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT");
  }

  public void testAttachingCorrectJavaDocsAndSources() throws Exception {
    deployArtifact("test", "foo", "1-SNAPSHOT",
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <artifactId>maven-source-plugin</artifactId>" +
                   "      <executions>" +
                   "        <execution>" +
                   "          <goals>" +
                   "            <goal>jar</goal>" +
                   "          </goals>" +
                   "        </execution>" +
                   "      </executions>" +
                   "    </plugin>" +
                   "    <plugin>" +
                   "      <artifactId>maven-javadoc-plugin</artifactId>" +
                   "      <executions>" +
                   "        <execution>" +
                   "          <goals>" +
                   "            <goal>jar</goal>" +
                   "          </goals>" +
                   "        </execution>" +
                   "      </executions>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>");

    removeFromLocalRepository("test");

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
    assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT");

    resolveDependenciesAndImport();
    downloadArtifacts();

    assertModuleLibDep("project",
                       "Maven: test:foo:1-SNAPSHOT",
                       "jar://" + getRepositoryPath() + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar!/",
                       "jar://" + getRepositoryPath() + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar!/");

    assertTrue(new File(getRepositoryFile(), "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar").exists());
    assertTrue(new File(getRepositoryFile(), "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar").exists());
    assertTrue(new File(getRepositoryFile(), "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar").exists());
  }

  private void deployArtifact(String groupId, String artifactId, String version) throws IOException {
    deployArtifact(groupId, artifactId, version, "");
  }

  private void deployArtifact(String groupId, String artifactId, String version, String tail) throws IOException {
    String moduleName = "___" + artifactId;

    createProjectSubFile(moduleName + "/src/main/java/Foo.java",
                         "/**\n" +
                         " * some doc\n" +
                         " */\n" +
                         "public class Foo { }");

    createModulePom(moduleName,
                    "<groupId>" + groupId + "</groupId>" +
                    "<artifactId>" + artifactId + "</artifactId>" +
                    "<version>" + version + "</version>" +

                    distributionManagementSection() +

                    tail);

    deploy(moduleName);
  }

  private void deploy(String modulePath) {
    executeGoal(modulePath, "deploy");
  }

  private String repositoriesSection() {
    return "<repositories>" +
           "  <repository>" +
           "    <id>internal</id>" +
           "    <url>file:///" + FileUtil.toSystemIndependentName(remoteRepoDir.getPath()) + "</url>" +
           "    <snapshots>" +
           "      <enabled>true</enabled>" +
           "      <updatePolicy>always</updatePolicy>" +
           "    </snapshots>" +
           "  </repository>" +
           "</repositories>";
  }

  private String distributionManagementSection() {
    return "<distributionManagement>" +
           "  <snapshotRepository>" +
           "    <id>internal</id>" +
           "    <url>file:///" + FileUtil.toSystemIndependentName(remoteRepoDir.getPath()) + "</url>" +
           "  </snapshotRepository>" +
           "</distributionManagement>";
  }
}