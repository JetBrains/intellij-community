package com.intellij.refactoring.ui;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Pair;

import java.util.Set;

public interface NameSuggestionsGenerator {
  SuggestedNameInfo getSuggestedNameInfo(PsiType type);

  Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type);
}
