package org.intellij.plugins.markdown.reference;

import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.jetbrains.annotations.NotNull;

public class CommonLinkDestinationReferenceTest extends BasePlatformTestCase {

  private static final Logger LOGGER = Logger.getInstance(CommonLinkDestinationReferenceTest.class);

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/reference/linkDestination/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFile("sample.md");
    myFixture.copyDirectoryToProject("app", "app");
  }

  private void doTest() {
    final PsiFile file = myFixture.getFile();
    final String fileText = file.getText();

    final int linkTitle = fileText.indexOf("[" + getTestName(true) + "]");
    assertTrue(linkTitle >= 0);

    final int app = fileText.indexOf("app", linkTitle);
    assertTrue(app >= 0);
    assertGoodReference(file, app);

    final int foo = fileText.indexOf("foo", app);
    assertTrue(foo >= 0);
    assertGoodReference(file, foo);
  }

  private static void assertGoodReference(PsiFile file, int app) {
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
  }

  public void testRef() {
    doTest();
  }

  public void testRefBrack() {
    doTest();
  }

  public void testLink() {
    doTest();
  }

  public void testLinkBrack() {
    doTest();
  }

  public void testLinkText() {
    PsiFile file = myFixture.getFile();
    String fileText = file.getText();
    assertSameReference(file, fileText.indexOf("[link]") + 1, fileText.indexOf("foo", fileText.indexOf("[link]")));
    assertSameReference(file, fileText.indexOf("[linkBrack]") + 1, fileText.indexOf("foo", fileText.indexOf("[linkBrack]")));
  }

  public void testInlineLinkLabel() {
    myFixture.addFileToProject("A.mdc", "# Yet another header");
    PsiFile file = myFixture.configureByText("C.md", "[Testing resolving](A.mdc#yet-another-header)");
    String fileText = file.getText();
    assertSameReference(file, fileText.indexOf("resolving") + 1, fileText.indexOf("header"));
  }

  public void testInlineWebLinkLabel() {
    PsiFile file = myFixture.configureByText("some.md", "[Google](http://app.com/foo.txt)");
    String fileText = file.getText();
    assertGoodReference(file, fileText.indexOf("app.com"));
  }

  public void testRelativeFileLinkLabel() {
    PsiFile file = myFixture.configureByText("some.md", "[Read Docs](app/foo.txt)");
    String fileText = file.getText();
    assertSameReference(file, fileText.indexOf("Read Docs") + 1, fileText.indexOf("foo.txt"));
  }

  public void testSamePageAnchorLinkLabel() {
    PsiFile file = myFixture.configureByText("some.md", "[Jump to Intro](#introduction)\n\n# Introduction");
    String fileText = file.getText();
    assertSameReference(file, fileText.indexOf("Jump to Intro") + 1, fileText.indexOf("introduction"));
  }

  public void testImageDescription() {
    PsiFile file = myFixture.configureByText("some.md", "![Logo](app/foo.txt)");
    String fileText = file.getText();
    assertSameReference(file, fileText.indexOf("Logo") + 1, fileText.indexOf("foo.txt"));
  }

  public void testUnresolvedReferenceHighlighting() {
    myFixture.enableInspections(new MarkdownUnresolvedFileReferenceInspection());
    myFixture.configureByText("a.md", "[xxx](app/)");
    myFixture.checkHighlighting();
  }

  private static void assertSameReference(PsiFile file, int linkTextOffset, int destinationOffset) {
    PsiReference linkTextReference = file.findReferenceAt(linkTextOffset);
    PsiReference destinationReference = file.findReferenceAt(destinationOffset);
    assertNotNull(linkTextReference);
    assertNotNull(destinationReference);
    assertEquals(destinationReference.resolve(), linkTextReference.resolve());
  }

  public void testTrailingSlashUrl() {
    final PsiReference reference = myFixture.getReferenceAtCaretPosition("trailingSlashUrl.md");
    assertNotNull(reference);
    // there are two reference providers adding same WebReference (AutoLinkWebReferenceContributor + CommonLinkDestinationReferenceContributor)
    for (PsiReference unwrappedRef : PsiReferenceUtil.unwrapMultiReference(reference)) {
      assertTrue((unwrappedRef.resolve()) instanceof FakePsiElement);
    }
  }
}
