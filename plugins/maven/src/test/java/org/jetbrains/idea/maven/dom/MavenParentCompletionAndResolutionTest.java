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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import org.jetbrains.idea.maven.dom.inspections.MavenParentMissedVersionInspection;
import org.jetbrains.idea.maven.dom.inspections.MavenPropertyInParentInspection;
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantGroupIdInspection;
import org.junit.Test;

import java.util.Collections;

public class MavenParentCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {

  @Test
  public void testVariants() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId><caret></groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "  <version></version>" +
                     "</parent>");
    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "junit");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>junit</groupId>" +
                     "  <artifactId><caret></artifactId>" +
                     "</parent>");
    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "junit");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>junit</groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "  <version><caret></version>" +
                     "</parent>");
    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "3.8.1", "3.8.2", "4.0");
  }

  @Test
  public void testResolutionInsideTheProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    importProjects(myProjectPom, m);

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>1</version>" +

                         "<parent>" +
                         "  <groupId><caret>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>");

    assertResolved(m, findPsiFile(myProjectPom));
  }

  public void testResolutionOutsideOfTheProject() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId><caret>junit</groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "  <version>4.0</version>" +
                     "</parent>");

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  @Test
  public void testResolvingByRelativePath() throws Throwable {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId><caret>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>parent/pom.xml</relativePath>" +
                     "</parent>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>");

    assertResolved(myProjectPom, findPsiFile(parent));
  }

  @Test
  public void testResolvingByRelativePathWithProperties() throws Throwable {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <parentPath>parent/pom.xml</parentPath>" +
                     "</properties>" +

                     "<parent>" +
                     "  <groupId><caret>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>${parentPath}</relativePath>" +
                     "</parent>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>");

    assertResolved(myProjectPom, findPsiFile(parent));
  }

  @Test
  public void testResolvingByRelativePathWhenOutsideOfTheProject() throws Throwable {
    VirtualFile parent = createPomFile(myProjectRoot.getParent(),
                                       "<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId><caret>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>../pom.xml</relativePath>" +
                     "</parent>");

    assertResolved(myProjectPom, findPsiFile(parent));
  }

  @Test
  public void testDoNotHighlightResolvedParentByRelativePathWhenOutsideOfTheProject() {
    createPomFile(myProjectRoot.getParent(),
                  "<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    VirtualFile projectPom = createProjectPom("<groupId>test</groupId>" +
                                              "<artifactId>project</artifactId>" +
                                              "<version>1</version>");

    importProject();

    setPomContent(projectPom,
                  "<warning descr=\"Definition of groupId is redundant, because it's inherited from the parent\">" +
                  "<groupId>test</groupId>" +
                  "</warning>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>parent</artifactId>" +
                  "  <version>1</version>" +
                  "  <relativePath>../pom.xml</relativePath>" +
                  "</parent>");

    myFixture.enableInspections(MavenRedundantGroupIdInspection.class);
    checkHighlighting(myProjectPom);
  }

  @Test
  public void testHighlightParentProperties() {
    assumeVersionMoreThan("3.5.0");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project0</artifactId>" +
                     "<version>1.${revision}</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>" +
                     "<properties>" +
                     "  <revision>0</revision>" +
                     "  <anotherProperty>0</anotherProperty>" +
                     "</properties>");

    VirtualFile m1= createModulePom("m1",
                    "<parent>" +
                    "<groupId>test</groupId>" +
                    "<artifactId>project0</artifactId>" +
                    "<version>1.${revision}</version>" +
                    "</parent>" +
                    "<artifactId>m1</artifactId>");

    VirtualFile m2 = createModulePom("m2",
                                     "<parent>" +
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project0</artifactId>" +
                                     "<version>1.${revision}</version>" +
                                     "</parent>" +
                                     "<artifactId>m1</artifactId>");

    importProject();

    m2 = createModulePom("m2",
                         """
                           <parent>
                           <groupId>test</groupId>
                           <artifactId><error descr="Properties in parent definition are prohibited">project${anotherProperty}</error></artifactId>
                           <version>1.${revision}</version>
                           </parent>
                           <artifactId>m1</artifactId>
                           """);

    myFixture.enableInspections(Collections.singletonList(MavenPropertyInParentInspection.class));
    checkHighlighting(m2);
  }

  @Test
  public void testHighlightParentPropertiesForMavenLess35() throws Exception {
    assumeVersionLessThan("3.5.0");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project0</artifactId>" +
                     "<version>1.${revision}</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>" +
                     "<properties>" +
                     "  <revision>0</revision>" +
                     "  <anotherProperty>0</anotherProperty>" +
                     "</properties>");

    createModulePom("m1",
                    "<parent>" +
                    "<groupId>test</groupId>" +
                    "<artifactId>project0</artifactId>" +
                    "<version>1.${revision}</version>" +
                    "</parent>" +
                    "<artifactId>m1</artifactId>");

    createModulePom("m2",
                    "<parent>" +
                    "<groupId>test</groupId>" +
                    "<artifactId>project0</artifactId>" +
                    "<version>1.${revision}</version>" +
                    "</parent>" +
                    "<artifactId>m1</artifactId>");

    importProject();

    VirtualFile m2 = createModulePom("m2",
                                     "<parent>" +
                                     "<groupId>test</groupId>" +
                                     "<artifactId><error descr=\"Properties in parent definition are prohibited\">project${anotherProperty}</error></artifactId>" +
                                     "<version><error descr=\"Properties in parent definition are prohibited\">1.${revision}</error></version>" +
                                     "</parent>" +
                                     "<artifactId>m1</artifactId>");

    myFixture.enableInspections(Collections.singletonList(MavenPropertyInParentInspection.class));
    checkHighlighting(m2);
  }

  @Test
  public void testRelativePathCompletion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath><caret></relativePath>" +
                     "</parent>");

    createModulePom("dir/one",
                    "<groupId>test</groupId>" +
                    "<artifactId>one</artifactId>" +
                    "<version>1</version>");

    createModulePom("two",
                    "<groupId>test</groupId>" +
                    "<artifactId>two</artifactId>" +
                    "<version>1</version>");

    assertCompletionVariants(myProjectPom, "dir", "two", "pom.xml");
  }

  @Test
  public void testRelativePathCompletion_2() {
    importProject("<groupId>test</groupId>" + "<artifactId>project</artifactId>" + "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" + "<artifactId>project</artifactId>" + "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>dir/<caret></relativePath>" +
                     "</parent>");

    createModulePom("dir/one", "<groupId>test</groupId>" + "<artifactId>one</artifactId>" + "<version>1</version>");

    createModulePom("dir/two", "<groupId>test</groupId>" + "<artifactId>two</artifactId>" + "<version>1</version>");
    createModulePom("dir", "<groupId>test</groupId>" + "<artifactId>two</artifactId>" + "<version>1</version>");

    assertCompletionVariants(myProjectPom, "one", "two", "pom.xml");
  }

  @Test
  public void testHighlightingUnknownValues() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId><error>xxx</error></groupId>" +
                     "  <artifactId><error>xxx</error></artifactId>" +
                     "  <version><error>xxx</error></version>" +
                     "</parent>");

    checkHighlighting();
  }

  @Test
  public void testHighlightingAbsentGroupId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<<error descr=\"'groupId' child tag should be defined\">parent</error>>" +
                     "  <artifactId><error>junit</error></artifactId>" +
                     "  <version><error>4.0</error></version>" +
                     "</parent>");
    importProjectWithErrors();
    checkHighlighting();
  }

  @Test
  public void testHighlightingAbsentArtifactId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<<error descr=\"'artifactId' child tag should be defined\">parent</error>>" +
                     "  <groupId>junit</groupId>" +
                     "  <version><error>4.0</error></version>" +
                     "</parent>");
    importProjectWithErrors();
    checkHighlighting();
  }

  @Test
  public void testHighlightingAbsentVersion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<<error descr=\"'version' child tag should be defined\">parent</error>>" +
                     "  <groupId>junit</groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "</parent>");
    importProjectWithErrors();

    myFixture.enableInspections(MavenParentMissedVersionInspection.class);
    checkHighlighting();
  }

  @Test
  public void testHighlightingInvalidRelativePath() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>junit</groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "  <version>4.0</version>" +
                     "  <relativePath><error>parent</error>/<error>pom.xml</error></relativePath>" +
                     "</parent>");

    checkHighlighting();
  }

  @Test
  public void testPathQuickFixForInvalidValue() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("bar",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>one</artifactId>" +
                                    "<version>1</version>");

    importProjects(myProjectPom, m);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>one</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath><caret>xxx</relativePath>" +
                     "</parent>");

    IntentionAction i = getIntentionAtCaret("Fix Relative Path");
    assertNotNull(i);

    myFixture.launchAction(i);
    PsiElement el = getElementAtCaret(myProjectPom);

    assertEquals("bar/pom.xml", ElementManipulators.getValueText(el));
  }

  @Test
  public void testDoNotShowPathQuickFixForValidPath() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("bar",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>one</artifactId>" +
                                    "<version>1</version>");

    importProjects(myProjectPom, m);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>one</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath><caret>bar/pom.xml</relativePath>" +
                     "</parent>");

    assertNull(getIntentionAtCaret("Fix relative path"));
  }
}
