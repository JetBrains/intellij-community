package org.jetbrains.idea.maven.dom;

public class PackagingCompletionTest extends MavenCompletionAndResolutionTestCase {
  public void testVariants() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    updateProjectPom("<groupId>test</groupId>" +
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
