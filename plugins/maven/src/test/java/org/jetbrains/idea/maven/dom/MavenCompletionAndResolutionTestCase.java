package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.IOException;
import java.util.ArrayList;
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

  protected void assertComplectionVariants(VirtualFile f, String... expected) throws IOException {
    myCodeInsightFixture.configureFromExistingVirtualFile(f);
    LookupElement[] variants = myCodeInsightFixture.completeBasic();

    List<String> actual = new ArrayList<String>();
    for (LookupElement each : variants) {
      actual.add(each.getLookupString());
    }

    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected PsiFile getPsiFile(VirtualFile f) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    return PsiDocumentManager.getInstance(myProject).getPsiFile(d);
  }
}