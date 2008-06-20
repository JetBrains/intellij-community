package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

public class ParentCompletionAndResolutionTest extends MavenCompletionAndResolutionWithIndicesTestCase {
  public void testVariants() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId><caret></groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "  <version><caret></version>" +
                     "</parent>");
    assertCompletionVariants(myProjectPom, "junit", "jmock", "test");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>junit</groupId>" +
                     "  <artifactId><caret></artifactId>" +
                     "</parent>");
    assertCompletionVariants(myProjectPom, "junit");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>junit</groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "  <version><caret></version>" +
                     "</parent>");
    assertCompletionVariants(myProjectPom, "3.8.1", "3.8.2", "4.0");
  }

  public void testResolutionInsideTheProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    importSeveralProjects(myProjectPom, m);

    updateModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId><caret>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");

    PsiReference ref = getReferenceAtCaret(m);
    assertNotNull(ref);
    assertEquals(getPsiFile(myProjectPom), ref.resolve());
  }

  public void testResolutionOutsideOfTheProject() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId><caret>junit</groupId>" +
                     "  <artifactId>junit</artifactId>" +
                     "  <version>4.0</version>" +
                     "</parent>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    String filePath = myIndicesFixture.getDataTestFixture().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(filePath);
    assertEquals(getPsiFile(f), ref.resolve());
  }

  public void testResolvingByRelativePath() throws Throwable {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    updateProjectPom("<groupId>test</groupId>" +
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

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    assertEquals(getPsiFile(parent), ref.resolve());
  }

  public void testRelativePathCompletion() throws Throwable {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    updateProjectPom("<groupId>test</groupId>" +
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

    assertCompletionVariants(myProjectPom, "dir/one/pom.xml", "two/pom.xml");
  }

  public void testHighlightingUnknownValues() throws Throwable {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId><error>xxx</error></groupId>" +
                  "  <artifactId><error>xxx</error></artifactId>" +
                  "  <version><error>xxx</error></version>" +
                  "</parent>");

    checkHighlighting();
  }

  public void testHighlightingInvalidRelativePath() throws Throwable {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>junit</groupId>" +
                  "  <artifactId>junit</artifactId>" +
                  "  <version>4.0</version>" +
                  "  <relativePath><error>parent/pom.xml</error></relativePath>" +
                  "</parent>");

    checkHighlighting();
  }

  public void testPathQuickFixForInvalidValue() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("bar",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>one</artifactId>" +
                                    "<version>1</version>");

    importSeveralProjects(myProjectPom, m);

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>one</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath><caret>xxx</relativePath>" +
                     "</parent>");

    IntentionAction i = getIntentionAtCaret("Fix relative path");
    assertNotNull(i);

    myCodeInsightFixture.launchAction(i);

    int offset = myCodeInsightFixture.getEditor().getCaretModel().getOffset();
    PsiElement el = myCodeInsightFixture.getFile().findElementAt(offset);

    assertEquals("bar/pom.xml", ElementManipulators.getValueText(el));
  }

  public void testDoNotShowPathQuickFixForValidPath() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("bar",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>one</artifactId>" +
                                    "<version>1</version>");

    importSeveralProjects(myProjectPom, m);

    updateProjectPom("<groupId>test</groupId>" +
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