/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Weighs down items starting with two underscores.
 * <br/>
 * User: dcheryasov
 */
public class PythonCompletionWeigher extends CompletionWeigher {

  public static final int WEIGHT_DELTA = 5;

  @NonNls private static final String DOUBLE_UNDER = "__";

  @Override
  public Comparable weigh(@NotNull final LookupElement element, @NotNull final CompletionLocation location) {
    if (!PsiUtilCore.findLanguageFromElement(location.getCompletionParameters().getPosition()).isKindOf(PythonLanguage.getInstance())) {
      return 0;
    }

    final String name = element.getLookupString();
    final LookupElementPresentation presentation = LookupElementPresentation.renderElement(element);
    // move dict keys to the top
    if ("dict key".equals(presentation.getTypeText())) {
      return element.getLookupString().length();
    }
    if (name.startsWith(DOUBLE_UNDER)) {
      if (name.endsWith(DOUBLE_UNDER)) return -2 * WEIGHT_DELTA; // __foo__ is lowest
      else return -WEIGHT_DELTA; // __foo is lower than normal
    }
    return 0; // default
  }
}
