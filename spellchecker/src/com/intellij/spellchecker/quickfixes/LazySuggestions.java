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
import com.intellij.spellchecker.SpellCheckerManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class LazySuggestions {
  private List<String> suggestions;
  private boolean processed;
  protected final String typo;

  public LazySuggestions(String typo) {
    this.typo = typo;
  }

  @NotNull
  public List<String> getSuggestions(Project project) {
    if (!processed) {
      suggestions = SpellCheckerManager.getInstance(project).getSuggestions(typo);
      processed = true;
    }
    return suggestions;
  }
}
