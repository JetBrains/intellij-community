package org.jetbrains.idea.maven;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SnapshotDependenciesImportingTest extends ImportingTestCase {
  private File repoDir;

  @Override
  protected void initDirs() throws IOException {
    super.initDirs();

    repoDir = new File(dir, "local");
    repoDir.mkdirs();
  }

  @Override
  protected void configMaven() {
    super.configMaven();
    getMavenCoreState().setLocalRepository(repoDir.getPath());
  }

  public void testSnapshotDependencyToLibrary() throws Exception {
    // this test indicates changes in maven embedder.
    // if it fails, then it is possible that either repository layout was changed
    // (that we assume in putArtifactInLocalRepository method),
    // or the logic of SNAPSHOT artifact resolution was changed.

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<repositories>" +
                     "  <repository>" +
                     "    <id>internal</id>" +
                     "    <url>file://" + getParentPath() + "</url>" +
                     "  </repository>" +
                     "</repositories>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>someGroup</groupId>" +
                     "    <artifactId>someArtifact</artifactId>" +
                     "    <version>1-SNAPSHOT</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    putArtifactInLocalRepository("someGroup", "someArtifact", "1-SNAPSHOT", "20000101120000", "1");
    importProject();

    assertModules("project");
    assertModuleLibDeps("project", "someGroup:someArtifact:1-20000101120000-1");
  }

  public void testSnapshotDependencyToModule() throws Exception {
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
                          "    <url>file://" + getParentPath() + "</url>" +
                          "  </repository>" +
                          "</repositories>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1-SNAPSHOT</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1-SNAPSHOT</version>");

    putArtifactInLocalRepository("test", "m2", "1-SNAPSHOT", "20000101120000", "2");
    importProject();

    assertModules("project", "m1", "m2");
    assertModuleModuleDeps("m1", "m2");
  }

  private void putArtifactInLocalRepository(String groupId, String artefactId, String version, String timestamp, String build) {
    String rawXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><metadata>" +
                 "  <groupId>%s</groupId>" +
                 "  <artifactId>%s</artifactId>" +
                 "  <version>%s</version>" +
                 "  <versioning>" +
                 "    <snapshot>" +
                 "      <timestamp>%s</timestamp>" +
                 "      <buildNumber>%s</buildNumber>" +
                 "    </snapshot>" +
                 "    <lastUpdated>0000000000000</lastUpdated>" +
                 "  </versioning>" +
                 "</metadata>";

    String xml = String.format(rawXml, groupId, artefactId, version, timestamp, build);

    String currentDate = new SimpleDateFormat("yyyy-MM-dd HH\\:mm\\:ss +0300").format(new Date());
    String prop = "internal.maven-metadata-internal.xml.lastUpdated=" + currentDate;

    String dir = groupId + "/" + artefactId + "/" + version + "/";

    String xmlName = dir + "/maven-metadata-internal.xml";
    String propName = dir + "/resolver-status.properties";

    writeFile(new File(repoDir, xmlName), xml);
    writeFile(new File(repoDir, propName), prop);
  }

  private void writeFile(File f, String string) {
    try {
      f.getParentFile().mkdirs();
      f.createNewFile();
      FileWriter w = new FileWriter(f);
      try {
        w.write(string);
      }
      finally {
        w.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}