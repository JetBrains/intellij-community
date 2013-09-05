package org.jetbrains.idea.maven.dom;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class MavenDependencyCompletionTest extends MavenDomWithIndicesTestCase {

  public void testCompletion() throws IOException {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    jun<caret>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariantsInclude(myProjectPom, "junit:junit");
  }

}
