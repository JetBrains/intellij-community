// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;
import org.junit.Test;

public class MavenExtensionCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {

  @Override
  protected MavenIndicesTestFixture createIndicesFixture() {
    return new MavenIndicesTestFixture(myDir.toPath(), myProject, "plugins");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Test
  public void testGroupIdCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension>" +
                     "      <groupId><caret></groupId>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, RENDERING_TEXT,
                             "org.apache.maven.plugins", "org.codehaus.plexus", "test", "intellij.test", "org.codehaus.mojo");
  }

  @Test 
  public void testArtifactIdCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId><caret></artifactId>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");


    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin",
                             "maven-resources-plugin", "maven-surefire-plugin", "maven-jar-plugin", "maven-clean-plugin",
                             "maven-install-plugin", "maven-compiler-plugin", "maven-deploy-plugin");
  }

  @Test 
  public void testArtifactWithoutGroupCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension>" +
                     "      <artifactId><caret></artifactId>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT,
                             "maven-clean-plugin",
                             "maven-jar-plugin",
                             "maven-war-plugin",
                             "maven-deploy-plugin",
                             "maven-resources-plugin",
                             "maven-eclipse-plugin",
                             "maven-install-plugin",
                             "maven-compiler-plugin",
                             "maven-site-plugin",
                             "maven-surefire-plugin",
                             "build-helper-maven-plugin",
                             "project");
  }

  @Test 
  public void testCompletionInsideTag() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension><caret></extension>" +
                     "  </extensions>" +
                     "</build>");

    assertDependencyCompletionVariantsInclude(myProjectPom,
                                              "org.apache.maven.plugins:maven-clean-plugin:2.5",
                                              "org.apache.maven.plugins:maven-compiler-plugin",
                                              "org.apache.maven.plugins:maven-deploy-plugin:2.7",
                                              "org.apache.maven.plugins:maven-eclipse-plugin:2.4",
                                              "org.apache.maven.plugins:maven-install-plugin:2.4",
                                              "org.apache.maven.plugins:maven-jar-plugin:2.4",
                                              "org.apache.maven.plugins:maven-resources-plugin:2.6",
                                              "org.apache.maven.plugins:maven-site-plugin:3.3",
                                              "org.apache.maven.plugins:maven-surefire-plugin",
                                              "org.apache.maven.plugins:maven-war-plugin:2.1-alpha-1",
                                              "org.codehaus.mojo:build-helper-maven-plugin:1.0",
                                              "test:project:1");
  }

  @Test 
  public void testResolving() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension>" +
                     "      <artifactId><caret>maven-compiler-plugin</artifactId>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");

    String pluginPath = "plugins/org/apache/maven/plugins/maven-compiler-plugin/3.1/maven-compiler-plugin-3.1.pom";
    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath(pluginPath);
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  @Test 
  public void testResolvingAbsentPlugins() {
    removeFromLocalRepository("org/apache/maven/plugins/maven-compiler-plugin");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension>" +
                     "      <artifactId><caret>maven-compiler-plugin</artifactId>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    ref.resolve(); // shouldn't throw;
  }

  @Test 
  public void testDoNotHighlightAbsentGroupIdAndVersion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");
    checkHighlighting();
  }

  @Test 
  public void testHighlightingAbsentArtifactId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <<error descr=\"'artifactId' child tag should be defined\">extension</error>>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");

    checkHighlighting();
  }
}
