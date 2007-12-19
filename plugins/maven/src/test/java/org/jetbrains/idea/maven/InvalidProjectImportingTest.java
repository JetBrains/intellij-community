package org.jetbrains.idea.maven;

import org.jetbrains.idea.maven.project.MavenException;

import java.io.IOException;

public class InvalidProjectImportingTest extends ImportingTestCase {
  public void testUnknownProblem() throws IOException, MavenException {
    try {
      importProjectUnsafe("");
      fail();
    }
    catch (MavenException e) {
      assertMessageContains(e, "java.lang.NullPointerException");
    }
  }

  public void testInvalidExtension() throws IOException, MavenException {
    try {
      importProjectUnsafe("<groupId>test</groupId>" +
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

  public void testInvalidProjectModelException() throws IOException, MavenException {
    try {
      importProjectUnsafe("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>project</version>" +
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

  public void testExceptionFromReadResolved() throws IOException, MavenException {
    try {
      importProjectUnsafe("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>project</version>" +
                          "<packaging>pom</packaging>" +

                          "<modules>" +
                          "  <module>foo</module>" +
                          "</modules>");
      fail();
    }
    catch (MavenException e) {
      assertMessageContains(e, "Failed to build MavenProject instance",
                               "foo\\pom.xml");
    }
  }

  private void assertMessageContains(MavenException e, String... parts) {
    for (String part : parts) {
      assertTrue("Substring '" + part + "' not fund in '" + e.getMessage() + "'",
                 e.getMessage().contains(part));
    }
  }
}
