package org.jetbrains.idea.maven;

import com.intellij.openapi.vfs.VirtualFile;
import hidden.org.codehaus.plexus.util.FileUtils;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.runner.executor.MavenEmbeddedExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class SnapshotDependenciesImportingTest extends ImportingTestCase {
  private File remoteRepoDir;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    remoteRepoDir = new File(dir, "remote");
    remoteRepoDir.mkdirs();

    removeFromLocalRepository("test");
  }

  @Override
  protected void tearDown() throws Exception {
    removeFromLocalRepository("test");
    super.tearDown();
  }

  public void testSnapshotVersionDependencyToModule() throws Exception {
    performTestWithDependencyVersion("1-SNAPSHOT");
  }

  public void testSnapshotRangeDependencyToModule() throws Exception {
    performTestWithDependencyVersion("SNAPSHOT");
  }

  private void performTestWithDependencyVersion(String version) throws IOException {
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

                          "<repositories>" +
                          "  <repository>" +
                          "    <id>internal</id>" +
                          "    <url>file://" + remoteRepoDir.getPath() + "</url>" +
                          "  </repository>" +
                          "</repositories>" +

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

                          "<distributionManagement>" +
                          "  <repository>" +
                          "    <id>internal</id>" +
                          "    <url>file://" + remoteRepoDir.getPath() + "</url>" +
                          "  </repository>" +
                          "</distributionManagement>");

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

  private void deploy(String modulePath) {
    VirtualFile pom = projectRoot.findFileByRelativePath(modulePath + "/pom.xml");
    MavenRunnerParameters rp = new MavenRunnerParameters(pom.getPath(), Arrays.asList("deploy"), null);
    MavenRunnerSettings rs = new MavenRunnerSettings();
    MavenEmbeddedExecutor e = new MavenEmbeddedExecutor(rp, getMavenCoreState(), rs);

    e.execute();
  }

  private void removeFromLocalRepository(String groupId) throws IOException {
    String path = getRepositoryPath() + "/" + groupId;
    FileUtils.deleteDirectory(path);
  }
}