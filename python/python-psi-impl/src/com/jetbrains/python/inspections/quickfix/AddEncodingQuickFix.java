// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PyEncodingUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * add missing encoding declaration
 * # -*- coding: <encoding name> -*-
 * to the source file
 * <p/>
 * User: catherine
 */
public class AddEncodingQuickFix extends PsiUpdateModCommandQuickFix {

  private final String myDefaultEncoding;
  private final int myEncodingFormatIndex;

  public AddEncodingQuickFix(String defaultEncoding, int encodingFormatIndex) {
    myDefaultEncoding = defaultEncoding;
    myEncodingFormatIndex = encodingFormatIndex;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.add.encoding");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiFile file = element.getContainingFile();
    if (file == null) return;
    PsiElement firstLine = file.getFirstChild();
    if (firstLine instanceof PsiComment && firstLine.getText().startsWith("#!")) {
      firstLine = firstLine.getNextSibling();
    }
    final LanguageLevel languageLevel = LanguageLevel.forElement(file);
    final String commentText = String.format(PyEncodingUtil.ENCODING_FORMAT_PATTERN[myEncodingFormatIndex], myDefaultEncoding);
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiComment encodingComment = elementGenerator.createFromText(languageLevel, PsiComment.class, commentText);
    encodingComment = (PsiComment)file.addBefore(encodingComment, firstLine);

    if (encodingComment.getNextSibling() == null || !encodingComment.getNextSibling().textContains('\n')) {
      file.addAfter(elementGenerator.createFromText(languageLevel, PsiWhiteSpace.class, "\n"), encodingComment);
    }
    updater.moveTo(encodingComment.getTextOffset() + encodingComment.getTextLength() + 1);
  }
}
