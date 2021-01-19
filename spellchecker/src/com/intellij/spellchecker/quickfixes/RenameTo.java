// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil.findInjectionHost;

public class RenameTo extends LazySuggestions implements SpellCheckerQuickFix {
  public RenameTo(String wordWithTypo) {
    super(wordWithTypo);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getFixName();
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
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    DictionarySuggestionProvider provider = findProvider();
    if (provider != null) {
      provider.setActive(true);
    }

    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return;
    final Editor editor = getEditor(psiElement, project);
    if (editor == null) return;

    SimpleDataContext.Builder builder = SimpleDataContext.builder();
    if (editor instanceof EditorWindow) {
      builder.add(CommonDataKeys.EDITOR, editor);
      builder.add(CommonDataKeys.PSI_ELEMENT, psiElement);
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      // TextEditorComponent / FiledEditorManagerImpl give away the data in real life
      PsiElement data = (PsiElement)new TextEditorPsiDataProvider()
        .getData(CommonDataKeys.PSI_ELEMENT.getName(), editor, editor.getCaretModel().getCurrentCaret());
      builder.add(CommonDataKeys.PSI_ELEMENT, data);
    }

    final Boolean selectAll = editor.getUserData(RenameHandlerRegistry.SELECT_ALL);
    try {
      editor.putUserData(RenameHandlerRegistry.SELECT_ALL, true);
      DataContext dataContext = builder.setParent(((EditorEx)editor).getDataContext()).build();
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

  @Nls
  public static String getFixName() {
    return SpellCheckerBundle.message("rename.to");
  }

  @Override
  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }

  @Nullable
  protected Editor getEditor(PsiElement element, @NotNull Project project) {
    return findInjectionHost(element) != null
           ? InjectedLanguageUtil.openEditorFor(element.getContainingFile(), project)
           : FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}