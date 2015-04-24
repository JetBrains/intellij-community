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
package com.jetbrains.python.formatter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Strip trailing blank lines at the end of the file if corresponding whitespace element belongs to formatted range/element.
 * Final whitespace is replaced by single line feed regardless of whether the option {@link EditorSettingsExternalizable#isEnsureNewLineAtEOF()}
 * was enabled, because it's required by PEP 8. Note however that this option is still necessary if file doesn't contain any whitespaces
 * at its end initially.
 *
 * @author Mikhail Golubev
 */
public class PyTrailingBlankLinesPostFormatProcessor implements PostFormatProcessor {
  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    if (source instanceof PyFile) {
      final PsiFile pyFile = (PsiFile)source;
      final PsiWhiteSpace lastWhitespace = as(pyFile.getLastChild(), PsiWhiteSpace.class);
      if (lastWhitespace != null) {
        replaceTrailingWhitespaceBySingleLineFeed(lastWhitespace);
      }
    }
    return source;
  }

  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (!(source instanceof PyFile)) {
      return rangeToReformat;
    }
    final PsiWhiteSpace lastWhitespace = as(source.getLastChild(), PsiWhiteSpace.class);
    if (lastWhitespace != null && rangeToReformat.intersects(lastWhitespace.getTextRange())) {
      final TextRange oldWhitespaceRange = lastWhitespace.getTextRange();
      final TextRange newWhitespaceRange = replaceTrailingWhitespaceBySingleLineFeed(lastWhitespace).getTextRange();

      final int delta = newWhitespaceRange.getLength() - oldWhitespaceRange.getLength();
      if (newWhitespaceRange.contains(oldWhitespaceRange)) {
        return newWhitespaceRange;
      }
      else if (rangeToReformat.contains(oldWhitespaceRange)) {
        return rangeToReformat.grown(delta);
      }
      else if (oldWhitespaceRange.getEndOffset() > rangeToReformat.getEndOffset()) {
        return new TextRange(rangeToReformat.getStartOffset(),
                             Math.min(rangeToReformat.getEndOffset(), newWhitespaceRange.getEndOffset()));
      }
      else if (oldWhitespaceRange.getStartOffset() < rangeToReformat.getStartOffset()) {
        final int unionLength = rangeToReformat.getEndOffset() - oldWhitespaceRange.getStartOffset();
        return TextRange.from(Math.max(oldWhitespaceRange.getStartOffset(), rangeToReformat.getStartOffset() + delta),
                              Math.min(rangeToReformat.getLength(), unionLength + delta));
      }
    }
    return rangeToReformat;
  }

  @NotNull
  private static PsiWhiteSpace replaceTrailingWhitespaceBySingleLineFeed(@NotNull final PsiWhiteSpace whitespace) {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(whitespace.getProject());
    final Document document = manager.getDocument(whitespace.getContainingFile());
    if (document != null) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(whitespace.getProject());
      final PsiWhiteSpace newWhitespace = generator.createPhysicalFromText(LanguageLevel.forElement(whitespace), PsiWhiteSpace.class, "\n");
      manager.doPostponedOperationsAndUnblockDocument(document);
      CodeStyleManager.getInstance(newWhitespace.getProject()).performActionWithFormatterDisabled(new Computable<PsiWhiteSpace>() {
        @Override
        public PsiWhiteSpace compute() {
          return (PsiWhiteSpace)whitespace.replace(newWhitespace);
        }
      });
    }
    return whitespace;
  }
}
