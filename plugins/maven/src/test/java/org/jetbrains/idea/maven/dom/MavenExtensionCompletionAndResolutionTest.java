/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

import java.util.HashSet;
import java.util.List;

public class MavenExtensionCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  private final static String[] ALL_EXTENSIONS = {"org.apache.maven.plugins:maven-war-plugin:2.1-alpha-1",
    "org.apache.maven.plugins:maven-deploy-plugin:2.7", "org.apache.maven.plugins:maven-resources-plugin:2.6",
    "test:project:1", "intellij.test:maven-extension:1.0", "org.apache.maven.plugins:maven-install-plugin:2.4",
    "org.apache.maven.plugins:maven-compiler-plugin:2.0.2", "org.codehaus.mojo:build-helper-maven-plugin:1.0",
    "org.apache.maven.plugins:maven-clean-plugin:2.5", "org.apache.maven.plugins:maven-jar-plugin:2.4",
    "org.codehaus.plexus:plexus-utils:1.1", "org.apache.maven.plugins:maven-eclipse-plugin:2.4",
    "org.apache.maven.plugins:maven-site-plugin:3.3", "org.apache.maven.plugins:maven-surefire-plugin:2.4.3"};
  @Override
  protected MavenIndicesTestFixture createIndicesFixture() {
    return new MavenIndicesTestFixture(myDir.toPath(), myProject, "plugins");
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

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

    assertCompletionVariants(myProjectPom,
                             ALL_EXTENSIONS);
  }

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

    List<String> actual = getCompletionVariants(myProjectPom);

    try {
      assertUnorderedElementsAreEqual(actual, "org.apache.maven.plugins:maven-clean-plugin:2.5",
                                      "org.apache.maven.plugins:maven-jar-plugin:2.4",
                                      "org.apache.maven.plugins:maven-war-plugin:2.1-alpha-1",
                                      "org.apache.maven.plugins:maven-deploy-plugin:2.7",
                                      "org.apache.maven.plugins:maven-resources-plugin:2.6",
                                      "org.apache.maven.plugins:maven-eclipse-plugin:2.4",
                                      "org.apache.maven.plugins:maven-install-plugin:2.4",
                                      "org.apache.maven.plugins:maven-compiler-plugin:2.0.2",
                                      "org.apache.maven.plugins:maven-site-plugin:3.3",
                                      "org.apache.maven.plugins:maven-surefire-plugin:2.4.3");
    }
    catch (Throwable t) {
      MavenProjectIndicesManager instance = MavenProjectIndicesManager.getInstance(myProject);
      System.out.println("GetArtifacts: " + new HashSet<>(instance.getArtifactIds("org.apache.maven.plugins")));
      System.out.println("Indexes: " + instance.getIndices());
      throw new AssertionError("GetArtifacts: " + instance.getArtifactIds("org.apache.maven.plugins") + " Indexes: " + instance.getIndices());
    }
  }

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

    assertCompletionVariants(myProjectPom,
                             "org.apache.maven.plugins:maven-clean-plugin:2.5", "org.apache.maven.plugins:maven-jar-plugin:2.4",
                             "org.apache.maven.plugins:maven-war-plugin:2.1-alpha-1", "org.apache.maven.plugins:maven-deploy-plugin:2.7",
                             "org.apache.maven.plugins:maven-resources-plugin:2.6", "org.apache.maven.plugins:maven-eclipse-plugin:2.4",
                             "org.apache.maven.plugins:maven-install-plugin:2.4", "org.apache.maven.plugins:maven-compiler-plugin:2.0.2",
                             "org.apache.maven.plugins:maven-site-plugin:3.3", "org.apache.maven.plugins:maven-surefire-plugin:2.4.3",
                             "org.codehaus.mojo:build-helper-maven-plugin:1.0");
  }

  public void testCompletionInsideTag() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <extensions>" +
                     "    <extension><caret></extension>" +
                     "  </extensions>" +
                     "</build>");

    assertCompletionVariants(myProjectPom,
                             "org.apache.maven.plugins:maven-clean-plugin:2.5", "org.apache.maven.plugins:maven-jar-plugin:2.4",
                             "org.apache.maven.plugins:maven-war-plugin:2.1-alpha-1", "org.apache.maven.plugins:maven-deploy-plugin:2.7",
                             "org.apache.maven.plugins:maven-resources-plugin:2.6", "org.apache.maven.plugins:maven-eclipse-plugin:2.4",
                             "org.apache.maven.plugins:maven-install-plugin:2.4", "org.apache.maven.plugins:maven-compiler-plugin:2.0.2",
                             "org.apache.maven.plugins:maven-site-plugin:3.3", "org.apache.maven.plugins:maven-surefire-plugin:2.4.3",
                             "org.codehaus.mojo:build-helper-maven-plugin:1.0");
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

    String pluginPath = "plugins/org/apache/maven/plugins/maven-compiler-plugin/3.1/maven-compiler-plugin-3.1.pom";
    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath(pluginPath);
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

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
