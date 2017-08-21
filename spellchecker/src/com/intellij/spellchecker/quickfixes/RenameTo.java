/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenameTo extends ShowSuggestions implements SpellCheckerQuickFix {

  public static final String FIX_NAME = "RenameTo";

  public RenameTo(String wordWithTypo) {
    super(wordWithTypo);
  }

  @NotNull
  public String getFamilyName() {
    return SpellCheckerBundle.message("rename.to");
  }

  @NotNull
  public String getName() {
    return FIX_NAME;
  }

  @Nullable
  private static DictionarySuggestionProvider findProvider() {
    Object[] extensions = Extensions.getExtensions(NameSuggestionProvider.EP_NAME);

    for (Object extension : extensions) {
      if (extension instanceof DictionarySuggestionProvider) {
        return (DictionarySuggestionProvider)extension;
      }
    }
    return null;
  }


  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    DictionarySuggestionProvider provider = findProvider();
    if (provider != null) {
      provider.setActive(true);
    }

    HashMap<String, Object> map = new HashMap<>();
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return;
    final Editor editor = getEditor(psiElement, project);
    if (editor == null) return;

    if (editor instanceof EditorWindow) {
      map.put(CommonDataKeys.EDITOR.getName(), editor);
      map.put(CommonDataKeys.PSI_ELEMENT.getName(), psiElement);
    } else if (ApplicationManager.getApplication().isUnitTestMode()) { // TextEditorComponent / FiledEditorManagerImpl give away the data in real life
      map.put(
        CommonDataKeys.PSI_ELEMENT.getName(),
        new TextEditorPsiDataProvider().getData(CommonDataKeys.PSI_ELEMENT.getName(), editor, editor.getCaretModel().getCurrentCaret())
      );
    }

    final Boolean selectAll = editor.getUserData(RenameHandlerRegistry.SELECT_ALL);
    try {
      editor.putUserData(RenameHandlerRegistry.SELECT_ALL, true);
      DataContext dataContext = SimpleDataContext.getSimpleContext(map, DataManager.getInstance().getDataContext(editor.getComponent()));
      AnAction action = new RenameElementAction();
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", dataContext);
      action.actionPerformed(event);
      if (provider != null) {
        provider.setActive(false);
      }
    }
    finally {
      editor.putUserData(RenameHandlerRegistry.SELECT_ALL, selectAll);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}