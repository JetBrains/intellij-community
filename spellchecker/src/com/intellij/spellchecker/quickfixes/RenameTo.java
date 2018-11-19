// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class RenameTo extends ShowSuggestions implements SpellCheckerQuickFix {

  public static final String FIX_NAME =  SpellCheckerBundle.message("rename.to");

  public RenameTo(String wordWithTypo) {
    super(wordWithTypo);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return FIX_NAME;
  }

  @Nullable
  private static DictionarySuggestionProvider findProvider() {
    for (Object extension : NameSuggestionProvider.EP_NAME.getExtensionList()) {
      if (extension instanceof DictionarySuggestionProvider) {
        return (DictionarySuggestionProvider)extension;
      }
    }
    return null;
  }


  @Override
  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }

  @Override
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
}