/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
    setLanguageLevel(LanguageLevel.PYTHON27);
    assertTrue(doTest("with open(foo) ", PyKeywordCompletionContributor.IN_WITH_AFTER_REF));
    assertFalse(doTest("with open(foo) as ", PyKeywordCompletionContributor.IN_WITH_AFTER_REF));
  }

  private boolean doTest(final String text, final ElementPattern<PsiElement> ref) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PsiElement element = myFixture.getFile().findElementAt(text.length() - 1);
    return ref.accepts(element);
  }
}
