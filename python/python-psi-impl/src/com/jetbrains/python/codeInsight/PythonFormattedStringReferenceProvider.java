// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyStringFormatParser;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class PythonFormattedStringReferenceProvider extends PsiReferenceProvider {
  @Override
  public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element, final @NotNull ProcessingContext context) {
    if (PythonFormattedStringReferenceContributor.Holder.FORMAT_STRING_PATTERN.accepts(element)) {
      return getReferencesFromFormatString((PyStringLiteralExpression)element);
    }
    else {
      return getReferencesFromPercentString((PyStringLiteralExpression)element);
    }
  }

  private static PySubstitutionChunkReference[] getReferencesFromFormatString(final @NotNull PyStringLiteralExpression element) {
    final List<PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.filterSubstitutions(
      PyStringFormatParser.parseNewStyleFormat(element.getText()));
    return getReferencesFromChunks(element, chunks);
  }

  private static PySubstitutionChunkReference[] getReferencesFromPercentString(final @NotNull PyStringLiteralExpression element) {
    final List<PyStringFormatParser.SubstitutionChunk>
      chunks = PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(element.getText()));
    return getReferencesFromChunks(element, chunks);
  }

  public static PySubstitutionChunkReference @NotNull [] getReferencesFromChunks(final @NotNull PyStringLiteralExpression element,
                                                                                 final @NotNull List<? extends PyStringFormatParser.SubstitutionChunk> chunks) {
    return ContainerUtil.map2Array(chunks, PySubstitutionChunkReference.class, chunk -> new PySubstitutionChunkReference(element, chunk));
  }
}
