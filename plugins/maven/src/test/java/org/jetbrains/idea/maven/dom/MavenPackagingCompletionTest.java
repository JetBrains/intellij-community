package org.jetbrains.idea.maven.dom;

public class MavenPackagingCompletionTest extends MavenDomTestCase {
  public void testVariants() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<packaging><caret></packaging>");

    assertCompletionVariants(myProjectPom, "jar", "pom", "war", "ejb", "ejb-client", "ear");
  }

  public void testDoNotHighlightUnknownPackagingTypes() throws Throwable {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<packaging>xxx</packaging>");

    checkHighlighting();
  }
}
