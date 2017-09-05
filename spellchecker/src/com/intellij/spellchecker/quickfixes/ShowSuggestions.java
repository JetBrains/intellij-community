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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;


public abstract class ShowSuggestions implements LocalQuickFix, Iconable {

  private List<String> suggestions;
  private boolean processed;
  private final String myWordWithTypo;


  public ShowSuggestions(String wordWithTypo) {
    myWordWithTypo = wordWithTypo;
  }

  @NotNull
  public List<String> getSuggestions(Project project){
    if (!processed){
      suggestions = SpellCheckerManager.getInstance(project).getSuggestions(myWordWithTypo);
      processed = true;
    }
    return suggestions;
  }

  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }

  @Nullable
  protected Editor getEditor(PsiElement element, @NotNull Project project) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (InjectedLanguageUtil.findInjectionHost(element) != null) {
      editor = InjectedLanguageUtil.openEditorFor(element.getContainingFile(), project);
    }
    return editor;
  }
}
