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
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PythonFormattedStringReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
    if (PythonFormattedStringReferenceContributor.FORMAT_STRING_PATTERN.accepts(element)) {
      return getReferencesFromFormatString((PyStringLiteralExpression)element);
    }
    else {
      return getReferencesFromPercentString((PyStringLiteralExpression)element);
    }
  }

  private static PySubstitutionChunkReference[] getReferencesFromFormatString(@NotNull final PyStringLiteralExpression element) {
    final List<PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.filterSubstitutions(
      PyStringFormatParser.parseNewStyleFormat(element.getText()));
    return getReferencesFromChunks(element, chunks);
  }

  private static PySubstitutionChunkReference[] getReferencesFromPercentString(@NotNull final PyStringLiteralExpression element) {
    final List<PyStringFormatParser.SubstitutionChunk>
      chunks = PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(element.getText()));
    return getReferencesFromChunks(element, chunks);
  }

  @NotNull
  public static PySubstitutionChunkReference[] getReferencesFromChunks(@NotNull final PyStringLiteralExpression element,
                                                                       @NotNull final List<PyStringFormatParser.SubstitutionChunk> chunks) {
    return ContainerUtil.map2Array(chunks, PySubstitutionChunkReference.class, chunk -> new PySubstitutionChunkReference(element, chunk));
  }
}
