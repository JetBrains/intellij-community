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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Handles extra blank lines at the end of the file if corresponding whitespace elements belong to formatted range/element.
 * These trailing whitespaces are replaced by line feeds if either:
 * <ul>
 * <li>Option {@link PyCodeStyleSettings#BLANK_LINE_AT_FILE_END} is enabled.</li>
 * <li>Setting {@link EditorSettingsExternalizable#isEnsureNewLineAtEOF()} is enabled. Otherwise extra new line added on the next
 * "Save" action will be removed after reformatting.</li>
 * </ul>
 * <em>and</em> file is not empty.
 * If none of these conditions holds, blank lines are removed completely.
 *
 * @author Mikhail Golubev
 */
public class PyTrailingBlankLinesPostFormatProcessor implements PostFormatProcessor {

  private static boolean isApplicableTo(@NotNull PsiFile source) {
    if (InjectedLanguageManager.getInstance(source.getProject()).isInjectedFragment(source)) {
      return false;
    }
    return source.getLanguage().isKindOf(PythonLanguage.getInstance());
  }

  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    final PsiFile psiFile = source.getContainingFile();
    if (isApplicableTo(psiFile)) {
      applyPendingChangesToPsi(source);
      final TextRange whitespaceRange = findTrailingWhitespacesRange(psiFile);
      if (source.getTextRange().intersects(whitespaceRange)) {
        replaceOrDeleteTrailingWhitespaces(psiFile, whitespaceRange);
      }
    }
    return source;
  }

  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (!isApplicableTo(source)) {
      return rangeToReformat;
    }
    applyPendingChangesToPsi(source);
    final TextRange oldWhitespaceRange = findTrailingWhitespacesRange(source);
    if (rangeToReformat.intersects(oldWhitespaceRange)) {
      final TextRange newWhitespaceRange = replaceOrDeleteTrailingWhitespaces(source, oldWhitespaceRange);
      final int delta = newWhitespaceRange.getLength() - oldWhitespaceRange.getLength();
      if (oldWhitespaceRange.contains(rangeToReformat)) {
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

  private static void applyPendingChangesToPsi(@NotNull PsiElement source) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(source.getContainingFile().getProject());
    final Document document = documentManager.getDocument(source.getContainingFile());
    if (document != null) {
      documentManager.doPostponedOperationsAndUnblockDocument(document);
    }
  }

  @NotNull
  private static TextRange findTrailingWhitespacesRange(@NotNull PsiFile file) {
    final CharSequence contents = file.getViewProvider().getContents();
    int start;
    boolean lineFeedNext = false;
    for (start = contents.length() - 1; start >= 0; start--) {
      final char c = contents.charAt(start);
      if (" \t\f\n\r".indexOf(c) < 0 && !(c == '\\' && (lineFeedNext || start == contents.length() - 1))) {
        break;
      }
      lineFeedNext = c == '\n';
    }
    return new TextRange(start + 1, contents.length());
  }

  @NotNull
  private static TextRange replaceOrDeleteTrailingWhitespaces(@NotNull final PsiFile pyFile, @NotNull final TextRange whitespaceRange) {
    final Project project = pyFile.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(pyFile);
    if (document != null) {
      final PyCodeStyleSettings customSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(PyCodeStyleSettings.class);
      final boolean addLineFeed = customSettings.BLANK_LINE_AT_FILE_END || EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF();
      try {
        final String text = addLineFeed ? "\n" : "";
        // Do not add extra blank line in empty file
        if (!text.isEmpty() && whitespaceRange.getStartOffset() != 0) {
          if (!whitespaceRange.isEmpty()) {
            document.replaceString(whitespaceRange.getStartOffset(), whitespaceRange.getEndOffset(), text);
          }
          else {
            document.insertString(document.getTextLength(), text);
          }
        }
        else if (!whitespaceRange.isEmpty()) {
          document.deleteString(whitespaceRange.getStartOffset(), whitespaceRange.getEndOffset());
        }
        return TextRange.from(whitespaceRange.getStartOffset(), text.length());
      }
      finally {
        documentManager.commitDocument(document);
      }
    }
    return whitespaceRange;
  }
}
