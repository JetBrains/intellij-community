/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PythonFormattedStringReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
    final PsiReference[] referencesFromFormatString = getReferencesFromFormatString((PyStringLiteralExpression)element);
    final PsiReference[] referencesFromPercentString = getReferencesFromPercentString((PyStringLiteralExpression)element);

    return ArrayUtil.mergeArrays(referencesFromFormatString, referencesFromPercentString);
  }

  private static PsiReference[] getReferencesFromFormatString(@NotNull final PyStringLiteralExpression element) {
    final List<PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.filterSubstitutions(
      PyStringFormatParser.parseNewStyleFormat(element.getStringValue()));
    return getReferencesFromChunks(element, chunks);
  }

  private static PsiReference[] getReferencesFromPercentString(@NotNull final PyStringLiteralExpression element) {
    final List<PyStringFormatParser.SubstitutionChunk>
      chunks = PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(element.getStringValue()));
    return getReferencesFromChunks(element, chunks);
  }

  @NotNull
  private static PsiReference[] getReferencesFromChunks(@NotNull final PyStringLiteralExpression element,
                                                        @NotNull final List<PyStringFormatParser.SubstitutionChunk> chunks) {
    final PsiReference[] result = new PsiReference[chunks.size()];
    if (!element.isDocString()) {
      for (int i = 0; i < chunks.size(); i++) {
        final PyStringFormatParser.SubstitutionChunk chunk = chunks.get(i);
        result[i] = new PySubstitutionChunkReference(element, chunk, i);
      }
    }
    return result;
  }
}
