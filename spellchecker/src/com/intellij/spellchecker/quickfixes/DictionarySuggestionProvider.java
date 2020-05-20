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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.PreferrableNameSuggestionProvider;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
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
  public SuggestedNameInfo getSuggestedNames(@NotNull PsiElement element, PsiElement nameSuggestionContext, @NotNull Set<String> result) {
    if (!active || nameSuggestionContext == null) {
      return null;
    }

    String initial = getText(nameSuggestionContext);
    if (initial == null) {
      return null;
    }

    String normalized = normalize(initial);

    Project project = element.getProject();
    SpellCheckerManager manager = SpellCheckerManager.getInstance(project);

    denormalize(initial, manager.getSuggestions(normalized))
      .stream()
      .filter(newName -> RenameUtil.isValidName(project, element, newName))
      .forEach(result::add);

    return SuggestedNameInfo.NULL_INFO;
  }

  private static @Nullable String getText(@NotNull PsiElement textElement) {
    String text = textElement.getText();

    if (textElement instanceof PsiNamedElement) {
      text = ((PsiNamedElement)textElement).getName();
    }

    return text;
  }

  private static @NotNull String normalize(@NotNull String text) {
    //Some languages may ask engine for suggestions with `"` included in word,
    //e.g. JavaScript -- "typpo" -- quotes would not be trimmed. We trim them here.
    return StringUtil.unquoteString(text);
  }

  private static @NotNull Collection<String> denormalize(@NotNull String initial, @NotNull Collection<String> suggestions) {
    if (!StringUtil.isQuotedString(initial)) {
      return suggestions;
    }

    char quote = initial.charAt(0);
    StringBuilder tmp = new StringBuilder();
    ArrayList<String> result = new ArrayList<>(suggestions.size());

    for (String suggestion : suggestions) {
      tmp.append(suggestion);
      StringUtil.quote(tmp, quote);
      result.add(tmp.toString());
      tmp.setLength(0);
    }

    return result;
  }
}
