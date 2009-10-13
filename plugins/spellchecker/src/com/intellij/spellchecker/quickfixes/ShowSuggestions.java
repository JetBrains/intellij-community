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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.SpellCheckerManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;


public abstract class ShowSuggestions implements LocalQuickFix, Iconable {

  protected TextRange textRange;
  protected String word;
  protected Project project;
  private List<String> suggestions;
  private boolean processed;




  public ShowSuggestions(@NotNull TextRange textRange, @NotNull String word, @NotNull Project project) {
    this.textRange = textRange;
    this.word = word;
    this.project = project;
  }

  @NotNull
  public List<String> getSuggestions(){
    if (!processed){
      calculateSuggestions();
      processed=true;
    }
    return suggestions;
  }

  private void calculateSuggestions(){
    SpellCheckerManager manager = SpellCheckerManager.getInstance(project);
    suggestions = manager.getSuggestions(word);
  }

  public Icon getIcon(int flags) {
    return new ImageIcon(ShowSuggestions.class.getResource("spellcheck.png"));
  }

}
