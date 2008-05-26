package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.lang.documentation.DocumentationProvider;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class MavenCompletionAndResolutionTestCase extends MavenImportingTestCase {
  protected CodeInsightTestFixture myCodeInsightFixture;
  private boolean myOriginalAutoCompletion;

  @Override
  protected void setUpCommonFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder().getFixture();

    myCodeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture);
    myCodeInsightFixture.setUp();

    myTempDirFixture = myCodeInsightFixture.getTempDirFixture();

    myCodeInsightFixture.enableInspections(MavenModelInspection.class);

    myOriginalAutoCompletion = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
  }

  @Override
  protected void tearDownCommonFixtures() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = myOriginalAutoCompletion;
    myCodeInsightFixture.tearDown();
  }

  protected PsiReference getReferenceAtCaret(VirtualFile f) throws IOException {
    myCodeInsightFixture.configureFromExistingVirtualFile(f);
    CaretModel e = myCodeInsightFixture.getEditor().getCaretModel();
    return myCodeInsightFixture.getFile().findReferenceAt(e.getOffset());
  }

  protected void assertCompletionVariants(VirtualFile f, String... expected) throws IOException {
    List<String> actual = getCompletionVariants(f);
    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected void assertCompletionVariantsInclude(VirtualFile f, String... expected) throws IOException {
    List<String> actual = getCompletionVariants(f);
    assertTrue(actual.toString(), actual.containsAll(Arrays.asList(expected)));
  }

  protected void assertCompletionVariantsDoNotInclude(VirtualFile f, String... expected) throws IOException {
    List<String> actual = getCompletionVariants(f);
    assertFalse(actual.toString(), new ArrayList<String>(Arrays.asList(expected)).removeAll(actual));
  }

  protected List<String> getCompletionVariants(VirtualFile f) throws IOException {
    myCodeInsightFixture.configureFromExistingVirtualFile(f);
    LookupElement[] variants = myCodeInsightFixture.completeBasic();

    List<String> result = new ArrayList<String>();
    for (LookupElement each : variants) {
      result.add(each.getLookupString());
    }
    return result;
  }

  protected void assertDocumentation(String expectedText) throws IOException {
    myCodeInsightFixture.configureFromExistingVirtualFile(myProjectPom);

    Editor editor = myCodeInsightFixture.getEditor();
    PsiFile psiFile = getPsiFile(myProjectPom);

    PsiElement originalElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    PsiElement targetElement = DocumentationManager.getInstance(myProject).findTargetElement(editor, psiFile, originalElement);

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(targetElement);
    assertEquals(expectedText, provider.generateDoc(targetElement, originalElement));

    // should work for lookup as well as for tags
    PsiElement lookupElement = provider.getDocumentationElementForLookupItem(PsiManager.getInstance(myProject), originalElement.getText(), originalElement);
    assertSame(targetElement, lookupElement);
  }

  protected void checkHighlighting() throws Throwable {
    myCodeInsightFixture.testHighlighting(true, true, true, myProjectPom);
  }

  protected PsiFile getPsiFile(VirtualFile f) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    return PsiDocumentManager.getInstance(myProject).getPsiFile(d);
  }
}