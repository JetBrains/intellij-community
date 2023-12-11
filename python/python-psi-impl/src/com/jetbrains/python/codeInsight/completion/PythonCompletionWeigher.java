/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;


/**
 * Weighs down items starting with two underscores.
 * <br/>
 */
public final class PythonCompletionWeigher extends CompletionWeigher {

  // TODO Unify different ways of detecting and weighing elements
  public static final int PRIORITY_WEIGHT = 5;
  public static final int WEIGHT_FOR_MULTIPLE_ARGUMENTS = 5;
  public static final int WEIGHT_FOR_KEYWORDS = 0;
  private static final Logger LOG = Logger.getInstance(PythonCompletionWeigher.class);
  public static final String COLLECTION_KEY = "dict key";
  private static final int COLLECTION_KEY_WEIGHT = 10;
  public static final int NOT_IMPORTED_MODULE_WEIGHT = -1;

  @Override
  public Comparable weigh(@NotNull final LookupElement element, @NotNull final CompletionLocation location) {
    if (!PsiUtilCore.findLanguageFromElement(location.getCompletionParameters().getPosition()).isKindOf(PythonLanguage.getInstance())) {
      return PyCompletionUtilsKt.FALLBACK_WEIGHT;
    }

    final String name = element.getLookupString();
    final LookupElementPresentation presentation = LookupElementPresentation.renderElement(element);
    // move dict keys to the top
    if (COLLECTION_KEY.equals(presentation.getTypeText())) {
      return COLLECTION_KEY_WEIGHT;
    }

    PsiElement psiElement = element.getPsiElement();
    PsiFile file = location.getCompletionParameters().getOriginalFile();
    if (psiElement != null) {
      if (psiElement.getContainingFile() == file) return PRIORITY_WEIGHT;

      PsiElement dummyParent = location.getCompletionParameters().getPosition().getParent();
      boolean isQualified = dummyParent instanceof PyReferenceExpression && ((PyReferenceExpression)dummyParent).isQualified();
      int completionWeight = PyCompletionUtilsKt.computeCompletionWeight(psiElement, name, null, file, isQualified);
      LOG.debug("Combined weight for completion item ", name, ": ", completionWeight);
      return completionWeight;
    }

    if (PyNames.isReserved(element.getLookupString())) {
      return WEIGHT_FOR_KEYWORDS;
    }

    if (element.getUserData(PyMultipleArgumentsCompletionContributor.Companion.getMULTIPLE_ARGUMENTS_VARIANT_KEY()) != null) {
      return WEIGHT_FOR_MULTIPLE_ARGUMENTS;
    }

    return PyCompletionUtilsKt.FALLBACK_WEIGHT;
  }
}
