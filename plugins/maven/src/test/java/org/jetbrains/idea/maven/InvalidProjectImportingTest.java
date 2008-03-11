package org.jetbrains.idea.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenException;

import java.util.ArrayList;
import java.util.List;

public class InvalidProjectImportingTest extends ImportingTestCase {
  public void testUnknownProblem() throws Exception {
    try {
      importProject("");
      fail();
    }
    catch (MavenException e) {
      assertMessageContains(e, "java.lang.NullPointerException");
    }
  }

  public void testInvalidExtension() throws Exception {
    try {
      importProject("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>1</version>" +

                          "<build>" +
                          " <extensions>" +
                          "   <extension>" +
                          "     <groupId>group</groupId>" +
                          "     <artifactId>bla-bla-bla</artifactId>" +
                          "    </extension>" +
                          "  </extensions>" +
                          "</build>");
      fail();
    }
    catch (MavenException e) {
      assertMessageContains(e, "group:bla-bla-bla");
    }
  }

  public void testInvalidProjectModelException() throws Exception {
    try {
      createModulePom("foo", "<groupId>test</groupId>" +
                             "<artifactId>foo</artifactId>" +
                             "<version>1</version>");

      importProject("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>1</version>" +
                          "<packaging>jar</packaging>" +

                          "<modules>" +
                          "  <module>foo</module>" +
                          "</modules>");
      fail();
    }
    catch (MavenException e) {
      assertMessageContains(e, "Packaging 'jar' is invalid");
    }
  }

  public void testExceptionFromReadResolved() throws Exception {
    try {
      createModulePom("foo", "<groupId>test</groupId>" +
                             "<artifactId>foo</artifactId>" +
                             "<version>1</version");

      importProject("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>project</version>" +
                          "<packaging>pom</packaging>" +

                          "<modules>" +
                          "  <module>foo</module>" +
                          "</modules>");
      fail();
    }
    catch (MavenException e) {
      assertMessageContains(e, "Failed to parse model",
                               "foo\\pom.xml");
    }
  }
  
  public void testReportingUnresolvedLibrariesProblems() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module>m3</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>xxx</groupId>" +
                          "    <artifactId>xxx</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>yyy</groupId>" +
                          "    <artifactId>yyy</artifactId>" +
                          "    <version>2</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>zzz</groupId>" +
                          "    <artifactId>zzz</artifactId>" +
                          "    <version>3</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>junit</groupId>" +
                          "    <artifactId>junit</artifactId>" +
                          "    <version>4.0</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();

    assertEquals(2, unresolvedArtifacts.size());

    MavenProject p1 = unresolvedArtifacts.get(0).first;
    MavenProject p2 = unresolvedArtifacts.get(1).first;

    assertEquals("m1", p1.getArtifactId());
    assertEquals("m2", p2.getArtifactId());

    assertArtifactsAre(unresolvedArtifacts.get(0).second, "xxx:xxx:1", "yyy:yyy:2");
    assertArtifactsAre(unresolvedArtifacts.get(1).second, "zzz:zzz:3");
  }
  
  public void testDoesNotReportInterModuleDependenciesAsUnresolved() throws Exception {
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

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertEquals(0, unresolvedArtifacts.size());
  }

  private void assertArtifactsAre(List<Artifact> actual, String... expectedNames) {
    List<String> actualNames = new ArrayList<String>();
    for (Artifact a : actual) {
      actualNames.add(a.getArtifactId() + ":" + a.getGroupId() + ":" + a.getVersion());
    }
    assertOrderedElementsAreEqual(actualNames, expectedNames);
  }

  private void assertMessageContains(MavenException e, String... parts) {
    for (String part : parts) {
      assertTrue("Substring '" + part + "' not fund in '" + e.getMessage() + "'",
                 e.getMessage().contains(part));
    }
  }
}
