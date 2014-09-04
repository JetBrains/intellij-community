/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.spellchecker.quickfixes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.PreferrableNameSuggestionProvider;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.util.containers.ContainerUtil;

import java.util.Set;


public class DictionarySuggestionProvider extends PreferrableNameSuggestionProvider {

  private boolean active;

  public void setActive(boolean active) {
    this.active = active;
  }

  @Override
  public boolean shouldCheckOthers() {
    return !active;
  }

  @Override
  public SuggestedNameInfo getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, Set<String> result) {
    assert result != null;
    if (!active || nameSuggestionContext==null) {
      return null;
    }
    String text = nameSuggestionContext.getText();
    if (nameSuggestionContext instanceof PsiNamedElement) {
      //noinspection ConstantConditions
      text = ((PsiNamedElement)element).getName();
    }
    if (text == null) {
      return null;
    }

    SpellCheckerManager manager = SpellCheckerManager.getInstance(element.getProject());

    ContainerUtil.addAllNotNull(result, manager.getSuggestions(text));
    return SuggestedNameInfo.NULL_INFO;
  }
}
