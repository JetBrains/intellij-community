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
package com.intellij.spellchecker;

import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.spellchecker.engine.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseSuggestionProvider implements SuggestionProvider {

  private final SpellCheckerManager manager;

  public BaseSuggestionProvider(@NotNull SpellCheckerManager manager) {
    this.manager = manager;
  }

  @Override
  @NotNull
  public List<String> getSuggestions(@NotNull String text) {

    String[] words = NameUtil.nameToWords(text);

    int index = 0;
    List[] res = new List[words.length];
    int i = 0;
    for (String word : words) {
      int start = text.indexOf(word, index);
      int end = start + word.length();
      if (manager.hasProblem(word)) {
        List<String> variants = manager.getRawSuggestions(word);
        res[i++] = variants;
      }
      else {
        List<String> variants = new ArrayList<>();
        variants.add(word);
        res[i++] = variants;
      }
      index = end;
    }

    int[] counter = new int[i];
    int size = 1;
    for (int j = 0; j < i; j++) {
      size *= res[j].size();
    }
    String[] all = new String[size];

    for (int k = 0; k < size; k++) {
      for (int j = 0; j < i; j++) {
        if (all[k] == null) {
          all[k] = "";
        }
        all[k] += res[j].get(counter[j]);
        counter[j]++;
        if (counter[j] >= res[j].size()) {
          counter[j] = 0;
        }
      }
    }

    return Arrays.asList(all);
  }
}
