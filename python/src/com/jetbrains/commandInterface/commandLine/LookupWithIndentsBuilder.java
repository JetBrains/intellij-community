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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;

// TODO: TEST
// TODO: This class out from here: it has nothing to do with command line nor with python

/**
 * <h1>Ident formatter</h1>
 * <h2>What does it do?</h2>
 * <p>
 * Creates list of lookup elements with priority and help text and correct indent.
 * Indent has size of longest element to make it pretty formatted:
 * <pre>
 *   command_1         : command help text
 *   very_long_command : help goes here
 *   spam              : again here
 * </pre>
 * </p>
 * <h2>How to use it?</h2>
 * <p>
 * Create it, fill with {@link #addElement(LookupElementBuilder, String)} or {@link #addElement(LookupElementBuilder, String, int)}
 * and obtain result with {@link #getResult()}.
 * </p>
 * <h2>Priority</h2>
 * <p>If <strong>at least</strong> one element has priority, elements would be prioritized. No priority will be used otherwise</p>
 *
 * @author Ilya.Kazakevich
 */
final class LookupWithIndentsBuilder {
  @NotNull
  private final Map<LookupElementBuilder, Pair<String, Integer>> myMap = new LinkedHashMap<>();
  private int myMaxLength;
  private boolean myHasPriority;

  /**
   * Adds element with priority. After this method called, all other elements should have priority.
   *
   * @param lookupElementBuilder lookup element
   * @param help                 help text
   * @param priority             priority
   */
  void addElement(@NotNull final LookupElementBuilder lookupElementBuilder,
                  @Nullable final String help,
                  final int priority) {
    addElementInternal(lookupElementBuilder, help, priority);
  }


  /**
   * Adds element with out of priority.
   *
   * @param lookupElementBuilder lookup element
   * @param help                 help text
   */
  void addElement(@NotNull final LookupElementBuilder lookupElementBuilder,
                  @Nullable final String help) {
    addElementInternal(lookupElementBuilder, help, null);
  }

  /**
   * Adds element with priority or not. After this method called with priority, all other elements should have priority.
   *
   * @param lookupElementBuilder lookup element
   * @param help                 help text
   * @param priority             priority or null
   */
  private void addElementInternal(@NotNull final LookupElementBuilder lookupElementBuilder,
                                  @Nullable final String help,
                                  @Nullable final Integer priority) {
    myMaxLength = Math.max(myMaxLength, lookupElementBuilder.getLookupString().length());
    myMap.put(lookupElementBuilder, Pair.create(help, priority));
    if (priority != null) {
      myHasPriority = true;
    }
  }

  /**
   * @return result lookup elements (to display in {@link PsiReference#getVariants()} for example)
   */
  @NotNull
  LookupElement[] getResult() {
    final List<LookupElement> result = new ArrayList<>(myMap.size());
    for (final Entry<LookupElementBuilder, Pair<String, Integer>> entry : myMap.entrySet()) {
      LookupElementBuilder elementBuilder = entry.getKey();
      final Pair<String, Integer> helpAndPriority = entry.getValue();
      final String help = helpAndPriority.first;

      if (!StringUtil.isEmptyOrSpaces(help)) {
        final int padding = myMaxLength - elementBuilder.getLookupString().length();
        elementBuilder = elementBuilder.withTailText(String.format("%s : %s", StringUtil.repeat(" ", padding), help));
      }
      if (myHasPriority) {
        // If we have priority and it is not provided for certain element we believe it is 0
        final int priority = (helpAndPriority.second == null ? 0 : helpAndPriority.second);
        result.add(PrioritizedLookupElement.withPriority(elementBuilder, priority));
      }
      else {
        result.add(elementBuilder);
      }
    }
    return result.toArray(new LookupElement[result.size()]);
  }
}
