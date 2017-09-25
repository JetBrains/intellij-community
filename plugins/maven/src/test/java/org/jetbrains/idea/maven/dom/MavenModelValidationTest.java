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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class MavenModelValidationTest extends MavenDomWithIndicesTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testCompletionRelativePath() throws Exception {
    createProjectSubDir("src");
    createProjectSubFile("a.txt", "");

    VirtualFile modulePom = createModulePom("module1",
                                            "<groupId>test</groupId>" +
                                            "<artifactId>module1</artifactId>" +
                                            "<version>1</version>" +
                                            "<parent>" +
                                            "<relativePath>../<caret></relativePath>" +
                                            "</parent>");

    assertCompletionVariants(modulePom, "src", "module1", "pom.xml");
  }

  public void testRelativePathDefaultValue() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile modulePom = createModulePom("module1",
                                            "<groupId>test</groupId>" +
                                            "<artifactId>module1</artifactId>" +
                                            "<version>1</version>" +
                                            "<parent>" +
                                            "<relativePath>../pom.<caret>xml</relativePath>" +
                                            "</parent>");

    configTest(modulePom);
    PsiElement elementAtCaret = myFixture.getElementAtCaret();

    assertInstanceOf(elementAtCaret, PsiFile.class);
    assertEquals(((PsiFile)elementAtCaret).getVirtualFile(), myProjectPom);
  }

  public void testUnderstandingProjectSchemaWithoutNamespace() {
    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <dep<caret>" +
                     "</project>");

    assertCompletionVariants(myProjectPom, "dependencies", "dependencyManagement");
  }

  public void testUnderstandingProfilesSchemaWithoutNamespace() {
    VirtualFile profiles = createProfilesXml("<profile>" +
                                             "  <<caret>" +
                                             "</profile>");

    assertCompletionVariantsInclude(profiles, "id", "activation");
  }

  public void testUnderstandingSettingsSchemaWithoutNamespace() throws Exception {
    VirtualFile settings = updateSettingsXml("<profiles>" +
                                             "  <profile>" +
                                             "    <<caret>" +
                                             "  </profile>" +
                                             "</profiles>");

    assertCompletionVariantsInclude(settings, "id", "activation");
  }

  public void testAbsentModelVersion() {
    myFixture.saveText(myProjectPom,
                     "<<error descr=\"'modelVersion' child tag should be defined\">project</error> xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                     "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                     "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                     "  <artifactId>foo</artifactId>" +
                     "</project>");
    checkHighlighting();
  }

  public void testAbsentArtifactId() {
    myFixture.saveText(myProjectPom,
                     "<<error descr=\"'artifactId' child tag should be defined\">project</error> xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                     "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                     "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "</project>");
    checkHighlighting();
  }

  public void testUnknownModelVersion() {
    myFixture.saveText(myProjectPom,
                     "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
                     "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                     "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                     "  <modelVersion><error descr=\"Unsupported model version. Only version 4.0.0 is supported.\">666</error></modelVersion>" +
                     "  <artifactId>foo</artifactId>" +
                     "</project>");
    checkHighlighting();
  }

  public void testEmptyValues() {
    createProjectPom("<<error>groupId</error>></groupId>" +
                     "<<error>artifactId</error>></artifactId>" +
                     "<<error>version</error>></version>");
    checkHighlighting();
  }

  public void testAddingSettingsXmlReadingProblemsToProjectTag() throws Exception {
    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</project>");
    updateSettingsXml("<<<");

    readProjects();

    myFixture.saveText(myProjectPom,
                     "<<error descr=\"'settings.xml' has syntax errors\">project</error>>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</project>");
    checkHighlighting();
  }

  public void testAddingProfilesXmlReadingProblemsToProjectTag() {
    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</project>");
    createProfilesXml("<<<");

    readProjects();

    myFixture.saveText(myProjectPom,
                     "<<error descr=\"'profiles.xml' has syntax errors\">project</error>>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</project>");
    checkHighlighting();
  }

  public void testAddingStructureReadingProblemsToParentTag() {
    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <parent>" +
                     "    <groupId>test</groupId>" +
                     "    <artifactId>project</artifactId>" +
                     "    <version>1</version>" +
                     "  </parent>" +
                     "</project>");

    readProjects();

    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <<error descr=\"Self-inheritance found\">parent</error>>" +
                     "    <groupId>test</groupId>" +
                     "    <artifactId>project</artifactId>" +
                     "    <version>1</version>" +
                     "  </parent>" +
                     "</project>");
    checkHighlighting(myProjectPom, true, false, true);
  }

  public void testAddingParentReadingProblemsToParentTag() {
    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +
                    "<<<");

    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <parent>" +
                     "    <groupId>test</groupId>" +
                     "    <artifactId>parent</artifactId>" +
                     "    <version>1</version>" +
                     "    <relativePath>parent/pom.xml</relativePath>" +
                     "  </parent>" +
                     "</project>");
    readProjects();

    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <<error descr=\"Parent 'test:parent:1' has problems\">parent</error>>" +
                     "    <groupId>test</groupId>" +
                     "    <artifactId>parent</artifactId>" +
                     "    <version>1</version>" +
                     "    <relativePath>parent/pom.xml</relativePath>" +
                     "  </parent>" +
                     "</project>");
    checkHighlighting();
  }

  public void testDoNotAddReadingSyntaxProblemsToProjectTag() {
    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <" +
                     "</project>");

    readProjects();

    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <" +
                     "<error><</error>/project>");
    checkHighlighting();
  }

  public void testDoNotAddDependencyAndModuleProblemsToProjectTag() {
    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <modules>" +
                     "    <module>foo</module>" +
                     "  </modules>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId>xxx</groupId>" +
                     "      <artifactId>yyy</artifactId>" +
                     "      <version>xxx</version>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</project>");

    readProjects();

    myFixture.saveText(myProjectPom,
                     "<project>" +
                     "  <modelVersion>4.0.0</modelVersion>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "  <modules>" +
                     "    <module><error>foo</error></module>" +
                     "  </modules>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId><error>xxx</error></groupId>" +
                     "      <artifactId><error>yyy</error></artifactId>" +
                     "      <version><error>xxx</error></version>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</project>");
    checkHighlighting();
  }
}
