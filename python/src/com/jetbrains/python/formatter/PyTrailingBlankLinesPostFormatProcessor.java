// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyUtil;
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

  @NotNull
  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    final PsiFile psiFile = source.getContainingFile();
    if (isApplicableTo(psiFile)) {
      final TextRange whitespaceRange = findTrailingWhitespacesRange(psiFile);
      if (source.getTextRange().intersects(whitespaceRange)) {
        replaceOrDeleteTrailingWhitespaces(psiFile, whitespaceRange);
      }
    }
    return source;
  }

  @NotNull
  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (!isApplicableTo(source)) {
      return rangeToReformat;
    }
    final TextRange oldWhitespaceRange = findTrailingWhitespacesRange(source);
    if (rangeToReformat.intersects(oldWhitespaceRange)) {
      final TextRange newWhitespaceRange = replaceOrDeleteTrailingWhitespaces(source, oldWhitespaceRange);
      return rangeToReformat.grown(oldWhitespaceRange.getLength() - newWhitespaceRange.getLength());
    }
    return rangeToReformat;
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
    final PyCodeStyleSettings customSettings = CodeStyle.getCustomSettings(pyFile, PyCodeStyleSettings.class);
    final boolean addLineFeed = customSettings.BLANK_LINE_AT_FILE_END || EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF();

    final String realWhitespace = whitespaceRange.substring(pyFile.getText());
    final String desiredWhitespace = addLineFeed ? "\n" : "";

    // Do not add extra blank line in an empty file
    if (!realWhitespace.equals(desiredWhitespace) && (whitespaceRange.getStartOffset() != 0 || desiredWhitespace.isEmpty())) {
      TextRange updatedRange = PyUtil.updateDocumentUnblockedAndCommitted(pyFile, document -> {
        document.replaceString(whitespaceRange.getStartOffset(), whitespaceRange.getEndOffset(), desiredWhitespace);
        return TextRange.from(whitespaceRange.getStartOffset(), desiredWhitespace.length());
      });
      if (updatedRange != null) {
        return updatedRange;
      }
    }
    return whitespaceRange;
  }
}
