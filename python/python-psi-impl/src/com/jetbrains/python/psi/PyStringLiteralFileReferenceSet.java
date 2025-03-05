// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralFileReferenceSet extends RootFileReferenceSet {
  public static final Pattern DELIMITERS = Pattern.compile("\\\\|/");
  private final PyStringLiteralExpression myStringLiteralExpression;


  public PyStringLiteralFileReferenceSet(@NotNull PyStringLiteralExpression element) {
    this(element, element.getContainingFile().getViewProvider().getVirtualFile().isCaseSensitive());
  }

  public PyStringLiteralFileReferenceSet(@NotNull PyStringLiteralExpression element, boolean caseSensitive) {
    this(element.getStringValue(), element, element.getStringValueTextRange().getStartOffset(), null, caseSensitive, true,
         FileType.EMPTY_ARRAY);
  }

  public PyStringLiteralFileReferenceSet(@NotNull String str,
                                         @NotNull PyStringLiteralExpression element,
                                         int startInElement,
                                         PsiReferenceProvider provider,
                                         boolean caseSensitive, boolean endingSlashNotAllowed, FileType @Nullable [] suitableFileTypes) {
    super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed,
          suitableFileTypes);
    myStringLiteralExpression = element;
    reparse();
  }

  @Override
  protected void reparse() {
    if (myStringLiteralExpression != null) {
      final List<FileReference> references = getFileReferences(myStringLiteralExpression);
      myReferences = references.toArray(FileReference.EMPTY);
    }
  }

  private @NotNull List<FileReference> getFileReferences(@NotNull PyStringLiteralExpression expression) {
    final String value = expression.getStringValue();
    final Matcher matcher = DELIMITERS.matcher(value);
    int start = 0;
    int index = 0;
    final List<FileReference> results = new ArrayList<>();
    while (matcher.find()) {
      final String s = value.substring(start, matcher.start());
      if (!s.isEmpty()) {
        final TextRange range = TextRange.create(expression.valueOffsetToTextOffset(start),
                                                 expression.valueOffsetToTextOffset(matcher.start()));
        results.add(createFileReference(range, index++, s));
      }
      start = matcher.end();
    }
    final String s = value.substring(start);
    if (!s.isEmpty()) {
      final TextRange range = TextRange.create(expression.valueOffsetToTextOffset(start),
                                               expression.valueOffsetToTextOffset(value.length()));
      results.add(createFileReference(range, index, s));
    }
    return results;
  }
}
