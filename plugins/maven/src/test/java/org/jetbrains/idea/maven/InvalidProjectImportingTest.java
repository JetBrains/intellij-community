package org.jetbrains.idea.maven;

import org.jetbrains.idea.maven.project.MavenException;

import java.io.File;

public class InvalidProjectImportingTest extends ImportingTestCase {
  public void testUnknownProblem() throws Exception {
    try {
      importProject("");
      fail();
    }
    catch (MavenException e) {
      assertExceptionHasPomFileAndContains(e, "java.lang.NullPointerException");
    }
  }

  public void testUnresolvedParent() throws Exception {
    try {
      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");
      fail();
    }
    catch (MavenException e) {
      assertExceptionHasPomFileAndContains(e, "Cannot find artifact for parent POM: test:parent::1");
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
      assertExceptionHasPomFileAndContains(e, "Packaging 'jar' is invalid");
    }
  }

  public void testExceptionFromReadResolved() throws Exception {
    try {
      createModulePom("foo", "<groupId>test</groupId>" +
                             "<artifactId>foo</artifactId>" +
                             "<version>1"); //  invalid tag

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
      assertExceptionHasPomFileAndContains(e, "end tag name </project> must be the same as start tag <version>");
    }
  }

  public void testInvalidRepositoryLayout() throws Exception {
    try {
      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +

                    "<distributionManagement>" +
                    "  <repository>" +
                    "    <id>test</id>" +
                    "    <url>http://www.google.com</url>" +
                    "    <layout>nothing</layout>" +
                    "  </repository>" +
                    "</distributionManagement>");
      fail();
    }
    catch (MavenException e) {
      assertExceptionHasPomFileAndContains(e, "Cannot find ArtifactRepositoryLayout instance for: nothing");
    }
  }
  
  public void testReportingDependenciesProblems() throws Exception {
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

    assertEquals(2, myResolutionProblems.size());

    File f1 = myResolutionProblems.get(0).first;
    File f2 = myResolutionProblems.get(1).first;

    assertTrue(f1.toString(), f1.getPath().endsWith("m1\\pom.xml"));
    assertTrue(f2.toString(), f2.getPath().endsWith("m2\\pom.xml"));

    assertOrderedElementsAreEqual(myResolutionProblems.get(0).second,
                                  "Unresolved dependency: xxx:xxx:jar:1:compile",
                                  "Unresolved dependency: yyy:yyy:jar:2:compile");
    assertOrderedElementsAreEqual(myResolutionProblems.get(1).second,
                                  "Unresolved dependency: zzz:zzz:jar:3:compile");
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
    assertEquals(0, myResolutionProblems.size());
  }

  public void testReportingInvalidExtensions() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <extensions>" +
                  "   <extension>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    assertEquals(1, myResolutionProblems.size());

    File f = myResolutionProblems.get(0).first;
    assertEquals(new File(myProjectPom.getPath()), f);
    assertOrderedElementsAreEqual(myResolutionProblems.get(0).second, "Unresolved build extension: xxx:yyy:jar:1");
  }
  
  public void testMultipleUnresolvedBuildExtensions() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    " <extensions>" +
                    "   <extension>" +
                    "     <groupId>xxx</groupId>" +
                    "     <artifactId>xxx</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "  </extensions>" +
                    "</build>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    " <extensions>" +
                    "   <extension>" +
                    "     <groupId>yyy</groupId>" +
                    "     <artifactId>yyy</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "   <extension>" +
                    "     <groupId>zzz</groupId>" +
                    "     <artifactId>zzz</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "  </extensions>" +
                    "</build>");

    importProject();

    assertEquals(myResolutionProblems.toString(), 3, myResolutionProblems.size());

    File f1 = myResolutionProblems.get(0).first;
    File f2 = myResolutionProblems.get(1).first;
    File f3 = myResolutionProblems.get(2).first;

    assertEquals(new File(myProjectPom.getPath()), f1);
    assertTrue(f2.toString(), f2.getPath().endsWith("m1\\pom.xml"));
    assertTrue(f3.toString(), f3.getPath().endsWith("m2\\pom.xml"));

    assertOrderedElementsAreEqual(myResolutionProblems.get(0).second,
                                  "Unresolved build extension: xxx:xxx:jar:1",
                                  "Unresolved build extension: yyy:yyy:jar:1",
                                  "Unresolved build extension: zzz:zzz:jar:1");
    assertOrderedElementsAreEqual(myResolutionProblems.get(1).second,
                                  "Unresolved build extension: xxx:xxx:jar:1");
    assertOrderedElementsAreEqual(myResolutionProblems.get(2).second,
                                  "Unresolved build extension: yyy:yyy:jar:1",
                                  "Unresolved build extension: zzz:zzz:jar:1");
  }

  private void assertExceptionContains(MavenException e, String... parts) {
    for (String part : parts) {
      assertTrue("Substring '" + part + "' not fund in '" + e.getMessage() + "'", e.getMessage().contains(part));
    }
  }

  private void assertExceptionHasPomFileAndContains(MavenException e, String... parts) {
    assertNotNull(e.getPomPath());
    assertExceptionContains(e, parts);
  }
}
