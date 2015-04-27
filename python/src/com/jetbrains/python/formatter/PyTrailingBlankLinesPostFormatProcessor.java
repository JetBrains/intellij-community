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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Strip trailing extra blank lines at the end of the file and insert necessary line feed if corresponding whitespace element belongs to
 * formatted range/element. If "Add new line at the file end" option was selected in Python code style settings final whitespace is replaced
 * by single line feed, and it removed completely otherwise.
 * <p/>
 * Note however that if option {@link EditorSettingsExternalizable#isEnsureNewLineAtEOF()} was also enabled line feed will be added at the
 * end of file on next "Save" action regardless of the code style settings for Python.
 *
 * @author Mikhail Golubev
 */
public class PyTrailingBlankLinesPostFormatProcessor implements PostFormatProcessor {
  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    if (source instanceof PyFile) {
      final PyFile pyFile = (PyFile)source;
      final List<PsiElement> range = findTrailingWhitespaces(pyFile);
      if (!range.isEmpty() &&
          PsiTreeUtil.isAncestor(source, ContainerUtil.getFirstItem(range), false) &&
          PsiTreeUtil.isAncestor(source, ContainerUtil.getLastItem(range), false)) {
        replaceOrDeleteTrailingWhitespaces(pyFile, range);
      }
    }
    return source;
  }

  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (!(source instanceof PyFile)) {
      return rangeToReformat;
    }
    final List<PsiElement> range = findTrailingWhitespaces(source);
    final TextRange oldWhitespaceRange = !range.isEmpty() ? unionRange(range) : TextRange.from(source.getTextLength(), 0);
    if (rangeToReformat.intersects(oldWhitespaceRange)) {
      final TextRange newWhitespaceRange = replaceOrDeleteTrailingWhitespaces((PyFile)source, range);;
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
  private static List<PsiElement> findTrailingWhitespaces(@NotNull PsiFile file) {
    final PsiElement lastLeaf = PsiTreeUtil.lastChild(file);

    if (isWhitespaceBackslashOrEmptyError(lastLeaf)) {
      final List<PsiElement> result = new ArrayList<PsiElement>();
      boolean containsWhitespaces = false;
      for (PsiElement prev = lastLeaf; isWhitespaceBackslashOrEmptyError(prev); prev = PsiTreeUtil.prevLeaf(prev)) {
        containsWhitespaces |= prev instanceof PsiWhiteSpace;
        result.add(prev);
      }
      if (containsWhitespaces) {
        Collections.reverse(result);
        return result;
      }
    }
    return Collections.emptyList();
  }

  private static boolean isWhitespaceBackslashOrEmptyError(@Nullable PsiElement elem) {
    if (elem == null) {
      return false;
    }
    return elem instanceof PsiWhiteSpace ||
           elem.getNode().getElementType() == PyTokenTypes.BACKSLASH ||
           (elem instanceof PsiErrorElement && elem.getTextLength() == 0);
  }

  @NotNull
  private static TextRange unionRange(@NotNull List<PsiElement> range) {
    if (range.isEmpty()) {
      return TextRange.EMPTY_RANGE;
    }
    //noinspection ConstantConditions
    return ContainerUtil.getFirstItem(range).getTextRange().union(ContainerUtil.getLastItem(range).getTextRange());
  }

  @NotNull
  private static TextRange replaceOrDeleteTrailingWhitespaces(@NotNull final PyFile pyFile,
                                                              @NotNull final List<PsiElement> whitespaces) {
    final Project project = pyFile.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(pyFile);
    if (document != null) {
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      int numLineFeedsAtEnd = CodeStyleSettingsManager.getSettings(project).getCustomSettings(PyCodeStyleSettings.class).NEW_LINE_AT_FILE_END;
      if (numLineFeedsAtEnd <= 0 && EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF()) {
        numLineFeedsAtEnd = 1;
      }
      if (numLineFeedsAtEnd > 0) {
        documentManager.doPostponedOperationsAndUnblockDocument(document);
        final PyElementGenerator generator = PyElementGenerator.getInstance(project);
        final String text = StringUtil.repeat("\n", numLineFeedsAtEnd);
        final LanguageLevel language = LanguageLevel.forElement(pyFile);
        // Wrapping whitespaces in parenthesis guarantees that they won't be splitted in several PSI element
        // (while Python's formatter operates this awkward way)
        final PsiWhiteSpace lineFeeds = generator.createFromText(language, PsiWhiteSpace.class, "(" + text + ")", new int[]{0, 0, 1});
        return codeStyleManager.performActionWithFormatterDisabled(new Computable<TextRange>() {
          @Override
          public TextRange compute() {
            if (!whitespaces.isEmpty()) {
              return replaceOrDeletePsiRange(whitespaces, lineFeeds).getTextRange();
            }
            else {
              return pyFile.add(lineFeeds).getTextRange();
            }
          }
        });
      }
      else if (!whitespaces.isEmpty()) {
        return codeStyleManager.performActionWithFormatterDisabled(new Computable<TextRange>() {
          @Override
          public TextRange compute() {
            replaceOrDeletePsiRange(whitespaces, null);
            return TextRange.from(pyFile.getTextLength(), 0);
          }
        });
      }
    }
    return whitespaces.isEmpty() ? TextRange.from(pyFile.getTextLength(), 0) : unionRange(whitespaces);
  }

  @Contract("_, null -> null; _, !null -> !null")
  @Nullable
  private static PsiElement replaceOrDeletePsiRange(@NotNull List<PsiElement> range, @Nullable PsiElement replacement) {
    final PsiElement file = range.get(0).getContainingFile();
    // If whitespaces span several parents, the safest option is to append new whitespace at the end of the file
    for (PsiElement element : ContainerUtil.reverse(range)) {
      element.delete();
    }
    if (replacement != null) {
      return file.add(replacement);
    }
    return null;
  }
}
