/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;

public class MavenModuleCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  private static final String CREATE_MODULE_INTENTION = MavenDomBundle.message("fix.create.module");
  private static final String CREATE_MODULE_WITH_PARENT_INTENTION = MavenDomBundle.message("fix.create.module.with.parent");

  public void testCompleteFromAllAvailableModules() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    VirtualFile module2Pom = createModulePom("m2",
                                             "<groupId>test</groupId>" +
                                             "<artifactId>m2</artifactId>" +
                                             "<version>1</version>" +
                                             "<packaging>pom</packaging>" +

                                             "<modules>" +
                                             "  <module>m3</module>" +
                                             "</modules>");

    createModulePom("m2/m3",
                    "<groupId>test</groupId>" +
                    "<artifactId>m3</artifactId>" +
                    "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2", "m3");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertCompletionVariants(myProjectPom, "m1", "m2", "m2/m3");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>1</version>" +
                          "<packaging>pom</packaging>" +

                          "<modules>" +
                          "  <module>m3</module>" +
                          "  <module><caret></module>" +
                          "</modules>");

    assertCompletionVariants(module2Pom, "..", "../m1", "m3");
  }

  public void testDoesNotCompeteIfThereIsNoModules() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertCompletionVariants(myProjectPom);
  }

  public void testIncludesAllThePomsAvailable() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createModulePom("subDir1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("subDir1/subDir2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertCompletionVariants(myProjectPom, "subDir1", "subDir1/subDir2");
  }

  public void testResolution() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m<caret>1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    assertResolved(myProjectPom, findPsiFile(m1), "m1");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m<caret>2</module>" +
                     "</modules>");

    assertResolved(myProjectPom, findPsiFile(m2), "m2");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>unknown<caret>Module</module>" +
                     "</modules>");

    assertUnresolved(myProjectPom, "unknownModule");
  }

  public void testResolutionWithSlashes() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>./m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>./m<caret></module>" +
                     "</modules>");

    assertResolved(myProjectPom, findPsiFile(m), "./m");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>.\\m<caret></module>" +
                     "</modules>");

    assertResolved(myProjectPom, findPsiFile(m), ".\\m");
  }

  public void testResolutionWithProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <dirName>subDir</dirName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>${dirName}/m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("subDir/m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <dirName>subDir</dirName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module><caret>${dirName}/m</module>" +
                     "</modules>");

    assertResolved(myProjectPom, findPsiFile(m), "subDir/m");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <dirName>subDir</dirName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>${<caret>dirName}/m</module>" +
                     "</modules>");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.dirName"));
  }

  public void testCreatePomQuickFix() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>subDir/new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_INTENTION);
    assertNotNull(i);

    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "subDir/newModule/pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <groupId>test</groupId>\n" +
      "    <artifactId>newModule</artifactId>\n" +
      "    <version>1</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testCreatePomQuickFixCustomPomFileName() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>subDir/new<caret>Module.xml</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_INTENTION);
    assertNotNull(i);

    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "subDir/newModule.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <groupId>test</groupId>\n" +
      "    <artifactId>subDir</artifactId>\n" +
      "    <version>1</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testCreatePomQuickFixInDotXmlFolder() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>subDir/new<caret>Module.xml</module>" +
                     "</modules>");
    createProjectSubFile("subDir/newModule.xml/empty"); // ensure that "subDir/newModule.xml" exists as a directory

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_INTENTION);
    assertNotNull(i);

    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "subDir/newModule.xml/pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <groupId>test</groupId>\n" +
      "    <artifactId>newModule.xml</artifactId>\n" +
      "    <version>1</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testCreatePomQuickFixTakesGroupAndVersionFromSuperParent() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +

                     "<parent>" +
                     "  <groupId>parentGroup</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>parentVersion</version>" +
                     "</parent>" +

                     "<modules>" +
                     "  <module>new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_INTENTION);
    assertNotNull(i);

    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "newModule/pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <groupId>parentGroup</groupId>\n" +
      "    <artifactId>newModule</artifactId>\n" +
      "    <version>parentVersion</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testCreatePomQuickFixWithProperties() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <dirName>subDir</dirName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>${dirName}/new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_INTENTION);
    assertNotNull(i);

    myFixture.launchAction(i);

    VirtualFile pom = myProjectRoot.findFileByRelativePath("subDir/newModule/pom.xml");
    assertNotNull(pom);
  }

  public void testCreatePomQuickFixTakesDefaultGroupAndVersionIfNothingToOffer() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_INTENTION);
    assertNotNull(i);
    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "newModule/pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <groupId>groupId</groupId>\n" +
      "    <artifactId>newModule</artifactId>\n" +
      "    <version>version</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testCreateModuleWithParentQuickFix() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_WITH_PARENT_INTENTION);
    assertNotNull(i);
    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "newModule/pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <parent>\n" +
      "        <groupId>test</groupId>\n" +
      "        <artifactId>project</artifactId>\n" +
      "        <version>1</version>\n" +
      "    </parent>\n" +
      "\n" +
      "    <groupId>test</groupId>\n" +
      "    <artifactId>newModule</artifactId>\n" +
      "    <version>1</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testCreateModuleWithParentQuickFix2() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>ppp/new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret(CREATE_MODULE_WITH_PARENT_INTENTION);
    assertNotNull(i);
    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "ppp/newModule/pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <parent>\n" +
      "        <groupId>test</groupId>\n" +
      "        <artifactId>project</artifactId>\n" +
      "        <version>1</version>\n" +
      "        <relativePath>../..</relativePath>\n" +
      "    </parent>\n" +
      "\n" +
      "    <groupId>test</groupId>\n" +
      "    <artifactId>newModule</artifactId>\n" +
      "    <version>1</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testCreateModuleWithParentQuickFix3() {
    VirtualFile parentPom = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>project</artifactId>" +
                                         "<version>1</version>" +
                                         "<packaging>pom</packaging>");

    importProject(parentPom);

    myFixture.saveText(parentPom, createPomXml(
                                "<groupId>test</groupId>" +
                                "<artifactId>project</artifactId>" +
                                "<version>1</version>" +
                                "<packaging>pom</packaging>" +

                                "<modules>" +
                                "  <module>../ppp/new<caret>Module</module>" +
                                "</modules>"));
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    IntentionAction i = getIntentionAtCaret(parentPom, CREATE_MODULE_WITH_PARENT_INTENTION);
    assertNotNull(i);
    myFixture.launchAction(i);

    assertCreateModuleFixResult(
      "ppp/newModule/pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
      "    <modelVersion>4.0.0</modelVersion>\n" +
      "\n" +
      "    <parent>\n" +
      "        <groupId>test</groupId>\n" +
      "        <artifactId>project</artifactId>\n" +
      "        <version>1</version>\n" +
      "        <relativePath>../../parent</relativePath>\n" +
      "    </parent>\n" +
      "\n" +
      "    <groupId>test</groupId>\n" +
      "    <artifactId>newModule</artifactId>\n" +
      "    <version>1</version>\n" +
      "\n" +
      "    \n" +
      "</project>");
  }

  public void testDoesNotShowCreatePomQuickFixForEmptyModuleTag() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertNull(getIntentionAtCaret(CREATE_MODULE_INTENTION));
  }

  public void testDoesNotShowCreatePomQuickFixExistingModule() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>module</module>" +
                     "</modules>");

    createModulePom("module",
                    "<groupId>test</groupId>" +
                    "<artifactId>module</artifactId>" +
                    "<version>1</version>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m<caret>odule</module>" +
                     "</modules>");

    assertNull(getIntentionAtCaret(CREATE_MODULE_INTENTION));
  }

  private void assertCreateModuleFixResult(String relativePath, String expectedText) {
    VirtualFile pom = myProjectRoot.findFileByRelativePath(relativePath);
    assertNotNull(pom);

    Document doc = FileDocumentManager.getInstance().getDocument(pom);

    Editor selectedEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    assertEquals(doc, selectedEditor.getDocument());

    assertEquals(expectedText, doc.getText());
  }
}
