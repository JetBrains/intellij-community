package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VfsUtil;

public class ModelValidationTest extends MavenCompletionAndResolutionWithIndicesTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testUnderstandingProjectsWithoutNamespace() throws Exception {
    VfsUtil.saveText(myProjectPom,
                     "<project>" +
                     "  <dep<caret>" +
                     "</project>");

    assertCompletionVariants(myProjectPom, "dependencies", "dependencyManagement");
  }
}