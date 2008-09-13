package com.intellij.refactoring.ui;

import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.SuggestedNameInfo;

public interface NameSuggestionsGenerator {
  SuggestedNameInfo getSuggestedNameInfo(PsiType type);

}
