/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author traff
 */
public class PyStringLiteralFileReferenceSet extends RootFileReferenceSet {
  public static final Pattern DELIMITERS = Pattern.compile("\\\\|/");
  private final PyStringLiteralExpression myStringLiteralExpression;


  public PyStringLiteralFileReferenceSet(@NotNull PyStringLiteralExpression element) {
    this(element, SystemInfo.isFileSystemCaseSensitive);
  }

  public PyStringLiteralFileReferenceSet(@NotNull PyStringLiteralExpression element, boolean caseSensitive) {
    this(element.getStringValue(), element, element.getStringValueTextRange().getStartOffset(), null, caseSensitive, true,
         FileType.EMPTY_ARRAY);
  }

  public PyStringLiteralFileReferenceSet(@NotNull String str,
                                         @NotNull PyStringLiteralExpression element,
                                         int startInElement,
                                         PsiReferenceProvider provider,
                                         boolean caseSensitive, boolean endingSlashNotAllowed, @Nullable FileType[] suitableFileTypes) {
    super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed,
          suitableFileTypes);
    myStringLiteralExpression = element;
    reparse();
  }

  @Override
  protected void reparse() {
    //noinspection ConstantConditions
    if (myStringLiteralExpression != null) {
      final List<FileReference> references = getFileReferences(myStringLiteralExpression);
      myReferences = references.toArray(new FileReference[references.size()]);
    }
  }

  @NotNull
  private List<FileReference> getFileReferences(@NotNull PyStringLiteralExpression expression) {
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
