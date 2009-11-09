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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.SpellCheckerManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;


public abstract class ShowSuggestions implements LocalQuickFix, Iconable {

  private List<String> suggestions;
  private boolean processed;
  protected ProblemDescriptor myProblemDescriptor;


  public ShowSuggestions() {
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
    SpellCheckerManager manager = SpellCheckerManager.getInstance(myProblemDescriptor.getPsiElement().getProject());
    suggestions = manager.getSuggestions(ProblemDescriptionNode.extractHighlightedText(myProblemDescriptor, myProblemDescriptor.getPsiElement()));
  }

  public Icon getIcon(int flags) {
    return new ImageIcon(ShowSuggestions.class.getResource("spellcheck.png"));
  }

  public void setDescriptor(ProblemDescriptor problemDescriptor) {
    myProblemDescriptor = problemDescriptor;
  }
}
