// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.ui.actions.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GenerateDomElementAction extends CodeInsightAction {

  protected final GenerateDomElementProvider myProvider;

  public GenerateDomElementAction(final @NotNull GenerateDomElementProvider generateProvider, @Nullable Icon icon) {
    getTemplatePresentation().setDescription(generateProvider.getDescription());
    getTemplatePresentation().setText(generateProvider.getText());
    getTemplatePresentation().setIcon(icon);

    myProvider = generateProvider;
    
  }

  public GenerateDomElementAction(final GenerateDomElementProvider generateProvider) {
      this(generateProvider, null);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
        final Runnable runnable = () -> {
          final DomElement element = myProvider.generate(project, editor, psiFile);
          myProvider.navigate(element);
        };
        
        if (GenerateDomElementAction.this.startInWriteAction()) {
          WriteCommandAction.writeCommandAction(project, psiFile).run(() -> runnable.run());
        }
        else {
          runnable.run();
        }
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  protected boolean startInWriteAction() {
    return true;
  }

  @Override
  protected boolean isValidForFile(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    final DomElement element = DomUtil.getContextElement(editor);
    return element != null && myProvider.isAvailableForElement(element);
  }

  public GenerateDomElementProvider getProvider() {
    return myProvider;
  }
}
