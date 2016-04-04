package com.jetbrains.edu.coursecreator;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;

public class CCRenameInputValidator implements RenameInputValidator {
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return PlatformPatterns.psiFile();
  }

  @Override
  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    if (!CCUtils.isAnswerFile(element)) {
      return true;
    }
    return newName.contains(".answer.");
  }
}
