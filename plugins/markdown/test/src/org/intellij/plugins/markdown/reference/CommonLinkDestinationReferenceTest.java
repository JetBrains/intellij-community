package org.intellij.plugins.markdown.reference;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/reference/linkDestination")
public class CommonLinkDestinationReferenceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("sample.md");
    myFixture.copyDirectoryToProject("app", "app");
  }

  private static final Logger LOGGER = Logger.getInstance(CommonLinkDestinationReferenceTest.class);

  private void doTest() {
    final PsiFile file = codeInsightFixture.get().getFile();
    final String fileText = ReadAction.computeBlocking(() -> file.getText());

    final int linkTitle = fileText.indexOf("[" + testName + "]");
    assertTrue(linkTitle >= 0);

    final int app = fileText.indexOf("app", linkTitle);
    assertTrue(app >= 0);
    assertGoodReference(file, app);

    final int foo = fileText.indexOf("foo", app);
    assertTrue(foo >= 0);
    assertGoodReference(file, foo);
  }

  private static void assertGoodReference(PsiFile file, int app) {
    ReadAction.run(() -> {
      final PsiReference reference = file.findReferenceAt(app);
      assertNotNull(reference);
      if (reference instanceof PsiPolyVariantReference) {
        assertTrue(((PsiPolyVariantReference)reference).multiResolve(false).length > 0);
      }
      else {
        assertNotNull(reference.resolve());
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.info(String.valueOf(reference.resolve().getClass()));
      }
    });
  }

  @Test
  public void testRef() {
    doTest();
  }

  @Test
  public void testRefBrack() {
    doTest();
  }

  @Test
  public void testLink() {
    doTest();
  }

  @Test
  public void testLinkBrack() {
    doTest();
  }

  @Test
  public void testLinkText() {
    PsiFile file = codeInsightFixture.get().getFile();
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertSameTarget(fileText.indexOf("[link]") + 1, fileText.indexOf("foo", fileText.indexOf("[link]")));
    assertSameTarget(fileText.indexOf("[linkBrack]") + 1, fileText.indexOf("foo", fileText.indexOf("[linkBrack]")));
  }

  @Test
  public void testWebLinkText() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("some.md", "[hello](https://www.google.com)");
    ReadAction.run(() -> {
      var targets = GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), file.getText().indexOf("hello") + 1);
      assertThat(targets).hasSize(1);
      var target = assertInstanceOf(PsiNamedElement.class, targets[0]);
      assertEquals("https://www.google.com", target.getName());
    });
  }

  @Test
  public void testJetBrainsLinkText() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("some.md", "[font](jetbrains://idea/settings?name=Editor--Font)");
    ReadAction.run(() -> {
      var targets = GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), file.getText().indexOf("font") + 1);
      assertThat(targets).hasSize(1);
      var target = assertInstanceOf(PsiNamedElement.class, targets[0]);
      assertEquals("jetbrains://idea/settings?name=Editor--Font", target.getName());
    });
  }

  @Test
  public void testImageInLinkText() {
    PsiFile file = codeInsightFixture.get().configureByText("some.md", "[![Logo](app/foo.txt)](https://www.google.com)");
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertSameTarget(fileText.indexOf("Logo") + 1, fileText.indexOf("foo.txt"));
    assertSameTarget(fileText.indexOf("foo.txt") + 1, fileText.indexOf("foo.txt"));
  }

  @Test
  public void testInlineLinkLabel() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("A.mdc", "# Yet another header");
    PsiFile file = myFixture.configureByText("C.md", "[Testing resolving](A.mdc#yet-another-header)");
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertSameTarget(fileText.indexOf("resolving") + 1, fileText.indexOf("header"));
  }

  @Test
  public void testInlineWebLinkLabel() {
    PsiFile file = codeInsightFixture.get().configureByText("some.md", "[Google](http://app.com/foo.txt)");
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertGoodReference(file, fileText.indexOf("app.com"));
  }

  @Test
  public void testRelativeFileLinkLabel() {
    PsiFile file = codeInsightFixture.get().configureByText("some.md", "[Read Docs](app/foo.txt)");
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertSameTarget(fileText.indexOf("Read Docs") + 1, fileText.indexOf("foo.txt"));
  }

  @Test
  public void testReferenceLinkText() {
    PsiFile file = codeInsightFixture.get().configureByText("some.md", "[Read Docs][file:docs]\n\n[file:docs]: app/foo.txt");
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertSameTarget(fileText.indexOf("Read Docs") + 1, fileText.indexOf("foo.txt"));
  }

  @Test
  public void testSamePageAnchorLinkLabel() {
    PsiFile file = codeInsightFixture.get().configureByText("some.md", "[Jump to Intro](#introduction)\n\n# Introduction");
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertSameTarget(fileText.indexOf("Jump to Intro") + 1, fileText.indexOf("introduction"));
  }

  @Test
  public void testImageDescription() {
    PsiFile file = codeInsightFixture.get().configureByText("some.md", "![Logo](app/foo.txt)");
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertSameTarget(fileText.indexOf("Logo") + 1, fileText.indexOf("foo.txt"));
  }

  @Test
  public void testUnresolvedReferenceHighlighting() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MarkdownUnresolvedFileReferenceInspection());
    myFixture.configureByText("a.md", "[xxx](app/)");
    myFixture.checkHighlighting();
  }

  private void assertSameTarget(int sourceOffset, int destinationOffset) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.run(() -> {
      var targets = GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), sourceOffset);
      var destinationReference = myFixture.getFile().findReferenceAt(destinationOffset);
      assertNotNull(destinationReference);
      assertThat(targets).hasSize(1);
      assertEquals(destinationReference.resolve(), targets[0]);
    });
  }

  @Test
  public void testTrailingSlashUrl() {
    final PsiReference reference = codeInsightFixture.get().getReferenceAtCaretPosition("trailingSlashUrl.md");
    ReadAction.run(() -> {
      assertNotNull(reference);
      // there are two reference providers adding same WebReference (AutoLinkWebReferenceContributor + CommonLinkDestinationReferenceContributor)
      for (PsiReference unwrappedRef : PsiReferenceUtil.unwrapMultiReference(reference)) {
        assertTrue((unwrappedRef.resolve()) instanceof FakePsiElement);
      }
    });
  }
}
