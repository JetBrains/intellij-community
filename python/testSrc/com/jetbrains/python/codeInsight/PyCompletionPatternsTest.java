package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyCompletionPatternsTest extends PyLightFixtureTestCase {
  public void testInFromImportAfterRef() {
    String text = "from . im";
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PsiElement element = myFixture.getFile().findElementAt(text.length() - 1);
    assertTrue(PyKeywordCompletionContributor.IN_FROM_IMPORT_AFTER_REF.accepts(element));
  }

  public void testAfterQualifier() {
    String text = "from . im";
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PsiElement element = myFixture.getFile().findElementAt(text.length() - 1);
    assertFalse(PyKeywordCompletionContributor.AFTER_QUALIFIER.accepts(element));
  }
}
