package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VfsUtil;

public class MavenModelValidationTest extends MavenCompletionAndResolutionWithIndicesTestCase {
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

  public void testAbsentModelVersion() throws Throwable {
    VfsUtil.saveText(myProjectPom,
                     "<<error descr=\"'modelVersion' child tag should be defined\">project</error> xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                     "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                     "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                     "  <artifactId>foo</artifactId>" +
                     "</project>");
    checkHighlighting();
  }

  public void testAbsentArtifactId() throws Throwable {
    VfsUtil.saveText(myProjectPom,
                     "<<error descr=\"'artifactId' child tag should be defined\">project</error> xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                     "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                     "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "</project>");
    checkHighlighting();
  }

  public void testUnknownModelVersion() throws Throwable {
    VfsUtil.saveText(myProjectPom,
                     "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                     "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                     "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                     "  <modelVersion><error descr=\"Unsupported model version. Only version 4.0.0 is supported.\">666</error></modelVersion>" +
                     "  <artifactId>foo</artifactId>" +
                     "</project>");
    checkHighlighting();
  }

  public void testEmptyValues() throws Throwable {
    createProjectPom("<<error>groupId</error>></groupId>" +
                     "<<error>artifactId</error>></artifactId>" +
                     "<<error>version</error>></version>");
    checkHighlighting();
  }
}
