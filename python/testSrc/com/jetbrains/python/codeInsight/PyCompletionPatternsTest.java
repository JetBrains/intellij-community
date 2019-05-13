// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.completion.PyKeywordCompletionContributor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyCompletionPatternsTest extends PyTestCase {
  public void testInFromImportAfterRef() {
    assertTrue(doTest("from . im", PyKeywordCompletionContributor.IN_FROM_IMPORT_AFTER_REF));
  }

  public void testAfterQualifier() {
    assertFalse(doTest("from . im", PyKeywordCompletionContributor.AFTER_QUALIFIER));
  }

  public void testWith() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON27,
      () -> {
        assertTrue(doTest("with open(foo) ", PyKeywordCompletionContributor.IN_WITH_AFTER_REF));
        assertFalse(doTest("with open(foo) as ", PyKeywordCompletionContributor.IN_WITH_AFTER_REF));
      }
    );
  }

  private boolean doTest(final String text, final ElementPattern<PsiElement> ref) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PsiElement element = myFixture.getFile().findElementAt(text.length() - 1);
    return ref.accepts(element);
  }
}
