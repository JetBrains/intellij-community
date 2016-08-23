/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class MavenExtensionCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Override
  protected MavenIndicesTestFixture createIndicesFixture() {
    return new MavenIndicesTestFixture(myDir, myProject, "plugins");
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testGroupIdCompletion() throws Exception {
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

    assertCompletionVariants(myProjectPom, "org.codehaus.plexus", "test", "org.apache.maven.plugins", "org.codehaus.mojo", "intellij.test");
  }

  public void testArtifactIdCompletion() throws Exception {
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

    List<String> actual = getCompletionVariants(myProjectPom);

    try {
      assertUnorderedElementsAreEqual(actual, "maven-compiler-plugin", "maven-war-plugin", "maven-eclipse-plugin", "maven-surefire-plugin");
    }
    catch (Throwable t) {
      MavenProjectIndicesManager instance = MavenProjectIndicesManager.getInstance(myProject);
      System.out.println("GetArtifacts: " + new HashSet<>(instance.getArtifactIds("org.apache.maven.plugins")));
      System.out.println("Indexes: " + instance.getIndices());

      for (MavenIndex index : instance.getIndices()) {
        System.out.println("Index: repositoryId=" + index.getRepositoryId() + " repositoryUrl=" + index.getRepositoryUrl() + " repositoryPathOrUrl" + index.getRepositoryPathOrUrl());
        System.out.println("Dir: " + index.getDir());
        index.printInfo();
      }

      throw new AssertionError("GetArtifacts: " + instance.getArtifactIds("org.apache.maven.plugins") + " Indexes: " + instance.getIndices());
    }
  }

  public void testArtifactWithoutGroupCompletion() throws Exception {
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

    assertCompletionVariants(myProjectPom,
                             "maven-compiler-plugin",
                             "maven-war-plugin",
                             "maven-surefire-plugin",
                             "build-helper-maven-plugin",
                             "maven-eclipse-plugin");
  }

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

    String pluginPath = "plugins/org/apache/maven/plugins/maven-compiler-plugin/2.0.2/maven-compiler-plugin-2.0.2.pom";
    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath(pluginPath);
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  public void testResolvingAbsentPlugins() throws Exception {
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

  public void testDoNotHighlightAbsentGroupIdAndVersion() throws Throwable {
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

  public void testHighlightingAbsentArtifactId() throws Throwable {
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
