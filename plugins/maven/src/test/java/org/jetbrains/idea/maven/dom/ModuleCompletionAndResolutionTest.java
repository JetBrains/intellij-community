package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;

import java.util.List;

public class ModuleCompletionAndResolutionTest extends MavenCompletionAndResolutionTestCase {
  public void testCompleteFromAllAvailableModules() throws Exception {
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

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertCompletionVariants(myProjectPom, "m1", "m2", "m2/m3");

    updateModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>pom</packaging>" +

                    "<modules>" +
                    "  <module>m3</module>" +
                    "  <module><caret></module>" +
                    "</modules>");

    assertCompletionVariants(module2Pom, "..", "../m1", "m3");
  }

  public void testDoesNotCompeteIfThereIsNoModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertCompletionVariants(myProjectPom);
  }

  public void testIncludesAllThePomsAvailable() throws Exception {
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

    updateProjectPom("<groupId>test</groupId>" +
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

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m<caret>1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");


    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    assertEquals("m1", ref.getCanonicalText());
    assertEquals(getPsiFile(m1), ref.resolve());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m<caret>2</module>" +
                     "</modules>");

    ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    assertEquals("m2", ref.getCanonicalText());
    assertEquals(getPsiFile(m2), ref.resolve());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>unknown<caret>Module</module>" +
                     "</modules>");

    ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    assertEquals("unknownModule", ref.getCanonicalText());
    assertNull(ref.resolve());
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

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>./m<caret></module>" +
                     "</modules>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    assertEquals("./m", ref.getCanonicalText());
    assertEquals(getPsiFile(m), ref.resolve());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>.\\m<caret></module>" +
                     "</modules>");

    ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    assertEquals(".\\m", ref.getCanonicalText());
    assertEquals(getPsiFile(m), ref.resolve());
  }

  public void testCreatePomQuickFix() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>subDir/new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret("Create pom.xml");
    assertNotNull(i);

    myCodeInsightFixture.launchAction(i);

    assertCreatePomFixResult(
        "subDir/newModule/pom.xml",
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
        "    <modelVersion>4.0.0</modelVersion>\n" +
        "    <groupId>test</groupId>\n" +
        "    <artifactId>newModule</artifactId>\n" +
        "    <version>1</version>\n" +
        "</project>");
  }

  public void testCreatePomQuickFixTakesGroupAndVersionFromSuperParent() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    updateProjectPom("<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +

                     "<parent>" +
                     "  <groupId>parentGroup</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>parentVersion</version>" +
                     "</parent>" +

                     "<modules>" +
                     "  <module>new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret("Create pom.xml");
    assertNotNull(i);

    myCodeInsightFixture.launchAction(i);

    assertCreatePomFixResult(
        "newModule/pom.xml",
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
        "    <modelVersion>4.0.0</modelVersion>\n" +
        "    <groupId>parentGroup</groupId>\n" +
        "    <artifactId>newModule</artifactId>\n" +
        "    <version>parentVersion</version>\n" +
        "</project>");
  }
  
  public void testCreatePomQuickFixTakesDefaultGroupAndVersionIfNothingToOffer() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    updateProjectPom("<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>new<caret>Module</module>" +
                     "</modules>");

    IntentionAction i = getIntentionAtCaret("Create pom.xml");
    assertNotNull(i);
    myCodeInsightFixture.launchAction(i);

    assertCreatePomFixResult(
        "newModule/pom.xml",
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
        "    <modelVersion>4.0.0</modelVersion>\n" +
        "    <groupId>groupId</groupId>\n" +
        "    <artifactId>newModule</artifactId>\n" +
        "    <version>version</version>\n" +
        "</project>");
  }

  public void testDoesNotShowCreatePomQuickFixForEmptyModuleTag() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertNull(getIntentionAtCaret("Create pom.xml"));
  }

  public void testDoesNotShowCreatePomQuickFixExistingModule() throws Throwable {
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

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m<caret>odule</module>" +
                     "</modules>");

    assertNull(getIntentionAtCaret("Create pom.xml"));
  }

  private void assertCreatePomFixResult(String relativePath, String expectedText) {
    VirtualFile pom = myProjectRoot.findFileByRelativePath(relativePath);
    assertNotNull(pom);

    Document doc = FileDocumentManager.getInstance().getDocument(pom);

    Editor selectedEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    assertEquals(doc, selectedEditor.getDocument());

    assertEquals(expectedText, doc.getText());
  }

  private IntentionAction getIntentionAtCaret(String intentionName) throws Throwable {
    myCodeInsightFixture.configureFromExistingVirtualFile(myProjectPom);
    List<IntentionAction> intentions = myCodeInsightFixture.getAvailableIntentions();

    return CodeInsightTestUtil.findIntentionByText(intentions, intentionName);
  }
}
