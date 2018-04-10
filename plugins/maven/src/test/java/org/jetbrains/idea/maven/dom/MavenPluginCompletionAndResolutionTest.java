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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;

import java.util.List;

public class MavenPluginCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
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
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId><caret></groupId>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "org.codehaus.plexus", "intellij.test", "test", "org.apache.maven.plugins", "org.codehaus.mojo");
  }

  public void testArtifactIdCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId><caret></artifactId>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin", "maven-resources-plugin",
                             "maven-surefire-plugin", "maven-jar-plugin", "maven-clean-plugin", "maven-install-plugin",
                             "maven-compiler-plugin", "maven-deploy-plugin");
  }

  public void testVersionCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <version><caret></version>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "RELEASE", "LATEST", "2.0.2", "3.1");
  }

  public void testArtifactWithoutGroupCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId><caret></artifactId>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom,
                             "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin", "maven-resources-plugin",
                             "maven-surefire-plugin", "maven-jar-plugin", "build-helper-maven-plugin", "maven-clean-plugin",
                             "maven-install-plugin", "maven-compiler-plugin", "maven-deploy-plugin");
  }

  public void testVersionWithoutGroupCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <version><caret></version>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "RELEASE", "LATEST", "2.0.2", "3.1");
  }

  public void testResolvingPlugins() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId><caret>maven-compiler-plugin</artifactId>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    String pluginPath = "plugins/org/apache/maven/plugins/maven-compiler-plugin/2.0.2/maven-compiler-plugin-2.0.2.pom";
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
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId><caret>maven-compiler-plugin</artifactId>" +
                     "    </plugin>" +
                     "  </plugins>" +
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
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");
    checkHighlighting();
  }

  public void testHighlightingAbsentArtifactId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <<error descr=\"'artifactId' child tag should be defined\">plugin</error>>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testBasicConfigurationCompletion() {
    putCaretInConfigurationSection();
    assertCompletionVariantsInclude(myProjectPom, "source", "target");
  }

  public void testIncludingConfigurationParametersFromAllTheMojos() {
    putCaretInConfigurationSection();
    assertCompletionVariantsInclude(myProjectPom, "excludes", "testExcludes");
  }

  private void putCaretInConfigurationSection() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <<caret>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");
  }

  public void testNoParametersForUnknownPlugin() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>unknown-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <<caret>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);
  }

  public void testNoParametersIfNothingIsSpecified() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <configuration>" +
                     "        <<caret>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);
  }

  public void testResolvingParamaters() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <<caret>includes></includes>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof XmlTag);
    assertEquals("parameter", ((XmlTag)resolved).getName());
    assertEquals("includes", ((XmlTag)resolved).findFirstSubTag("name").getValue().getText());
  }

  public void testResolvingInnerParamatersIntoOuter() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <includes>" +
                     "          <<caret>include></include" +
                     "        </includes>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof XmlTag);
    assertEquals("parameter", ((XmlTag)resolved).getName());
    assertEquals("includes", ((XmlTag)resolved).findFirstSubTag("name").getValue().getText());
  }

  public void testGoalsCompletionAndHighlighting() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal><caret></goal>" +
                     "          </goals>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "help", "compile", "testCompile");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal><error>xxx</error></goal>" +
                     "          </goals>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testDontHighlightGoalsForUnresolvedPlugin() {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +

                     "<build>\n" +
                     "  <plugins>\n" +
                     "    <plugin>\n" +
                     "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                     "      <executions>\n" +
                     "        <execution>\n" +
                     "          <goals>\n" +
                     "            <goal>compile</goal>\n" +
                     "            <goal><error>unknownGoal</error></goal>\n" +
                     "          </goals>\n" +
                     "        </execution>\n" +
                     "      </executions>\n" +
                     "    </plugin>\n" +
                     "    <plugin>\n" +
                     "      <artifactId><error>unresolved-plugin</error></artifactId>\n" +
                     "      <executions>\n" +
                     "        <execution>\n" +
                     "          <goals>\n" +
                     "            <goal>compile</goal>\n" +
                     "            <goal>unknownGoal</goal>\n" +
                     "          </goals>\n" +
                     "        </execution>\n" +
                     "      </executions>\n" +
                     "    </plugin>\n" +
                     "  </plugins>\n" +
                     "</build>");

    checkHighlighting();
  }

  public void testGoalsCompletionAndHighlightingInPluginManagement() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <pluginManagement>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal><caret></goal>" +
                     "          </goals>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "  </pluginManagement>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "help", "compile", "testCompile");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <pluginManagement>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal><error>xxx</error></goal>" +
                     "          </goals>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "  </pluginManagement>" +
                     "</build>");

    checkHighlighting();
  }

  public void testGoalsResolution() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal><caret>compile</goal>" +
                     "          </goals>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof XmlTag);
    assertEquals("mojo", ((XmlTag)resolved).getName());
    assertEquals("compile", ((XmlTag)resolved).findFirstSubTag("goal").getValue().getText());
  }

  public void testGoalsCompletionAndResolutionForUnknownPlugin() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>xxx</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal><caret></goal>" +
                     "          </goals>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>xxx</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal><caret>compile</goal>" +
                     "          </goals>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertUnresolved(myProjectPom);
  }

  public void testPhaseCompletionAndHighlighting() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <phase><caret></phase>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariantsInclude(myProjectPom, "clean", "compile", "package");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <phase><error>xxx</error></phase>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testNoExecutionParametersIfNoGoalNorIdAreSpecified() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <configuration>" +
                     "            <<caret>" +
                     "          </configuration>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);
  }

  public void testExecutionParametersForSpecificGoal() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal>compile</goal>" +
                     "          </goals>" +
                     "          <configuration>" +
                     "            <<caret>" +
                     "          </configuration>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertTrue(variants.toString(), variants.contains("excludes"));
    assertFalse(variants.toString(), variants.contains("testExcludes"));
  }

  public void testExecutionParametersForDefaultGoalExecution() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <id>default-compile</id>" +
                     "          <configuration>" +
                     "            <<caret>" +
                     "          </configuration>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertTrue(variants.toString(), variants.contains("excludes"));
    assertFalse(variants.toString(), variants.contains("testExcludes"));
  }

  public void testExecutionParametersForSeveralSpecificGoals() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal>compile</goal>" +
                     "            <goal>testCompile</goal>" +
                     "          </goals>" +
                     "          <configuration>" +
                     "            <<caret>" +
                     "          </configuration>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariantsInclude(myProjectPom, "excludes", "testExcludes");
  }

  public void testAliasCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-war-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <<caret>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariantsInclude(myProjectPom, "warSourceExcludes", "excludes");
  }

  public void testListElementsCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <excludes>" +
                     "          <exclude></exclude>" +
                     "          <<caret>" +
                     "        </excludes>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "exclude");
  }

  public void testListElementWhatHasUnpluralizedNameCompletion() {
    // NPE test - StringUtil.unpluralize returns null.

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-eclipse-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <additionalConfig>" +
                     "          <<caret>" +
                     "        </additionalConfig>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "additionalConfig", "config");
  }

  public void testDoNotHighlightUnknownElementsUnderLists() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <excludes>" +
                     "          <foo>foo</foo>" +
                     "        </excludes>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testArrayElementsCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-war-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <webResources>" +
                     "          <webResource></webResource>" +
                     "          <<caret>" +
                     "        </webResources>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "resource", "webResource");
  }

  public void testCompletionInCustomObjects() {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-war-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <webResources>" +
                     "          <webResource>" +
                     "            <<caret>" +
                     "          </webResource>" +
                     "        </webResources>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);
  }

  public void testDocumentationForParameter() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <s<caret>ource></source>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertDocumentation("Type: <b>java.lang.String</b><br>Expression: <b>${maven.compiler.source}</b><br><br><i>The -source argument for the Java compiler.</i>");
  }

  public void testDoNotCompleteNorHighlightNonPluginConfiguration() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<ciManagement>" +
                     "  <system>foo</system>" +
                     "  <notifiers>" +
                     "    <notifier>" +
                     "      <type>mail</type>" +
                     "      <configuration>" +
                     "        <address>foo@bar.com</address>" +
                     "      </configuration>" +
                     "    </notifier>" +
                     "  </notifiers>" +
                     "</ciManagement>");

    checkHighlighting();
  }

  public void testDoNotHighlightInnerParameters() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <source>" +
                     "          <foo>*.java</foo>" +
                     "        </source>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testDoNotHighlightRequiredParametersWithDefaultValues() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-surefire-plugin</artifactId>" +
                     "      <version>2.4.3</version>" +
                     "      <configuration>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting(); // surefire plugin has several required parameters with default values.
  }

  public void testDoNotHighlightInnerParameterAttributes() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <includes value1='1'>" +
                     "          <include value2='2'/>" +
                     "        </includes>" +
                     "        <source value3='3'>" +
                     "          <child value4='4'/>" +
                     "        </source>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testDoNotCompleteParameterAttributes() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <source <caret>/>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom, "combine.children", "combine.self");
  }

  public void testWorksWithPropertiesInPluginId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <plugin.groupId>org.apache.maven.plugins</plugin.groupId>" +
                     "  <plugin.artifactId>maven-compiler-plugin</plugin.artifactId>" +
                     "  <plugin.version>2.0.2</plugin.version>" +
                     "</properties>");
    importProject(); // let us recognize the properties first

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <plugin.groupId>org.apache.maven.plugins</plugin.groupId>" +
                     "  <plugin.artifactId>maven-compiler-plugin</plugin.artifactId>" +
                     "  <plugin.version>2.0.2</plugin.version>" +
                     "</properties>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>${plugin.groupId}</groupId>" +
                     "      <artifactId>${plugin.artifactId}</artifactId>" +
                     "      <version>${plugin.version}</version>" +
                     "      <configuration>" +
                     "        <source></source>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testDoNotHighlightPropertiesForUnknownPlugins() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId><error>foo.bar</error></artifactId>" +
                     "      <configuration>" +
                     "        <prop>" +
                     "          <value>foo</value>" +
                     "        </prop>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    checkHighlighting();
  }

  public void testTellNobodyThatIdeaIsRulezzz() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <includes>" +
                     "          <bar a<caret> />" +
                     "        </includes>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);
  }
}
