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

package com.intellij.util.xml.ui.actions.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: Sergey.Vasiliev
 */
public class GenerateDomElementAction extends CodeInsightAction {

  protected final GenerateDomElementProvider myProvider;

  public GenerateDomElementAction(@NotNull final GenerateDomElementProvider generateProvider, @Nullable Icon icon) {
    getTemplatePresentation().setDescription(generateProvider.getDescription());
    getTemplatePresentation().setText(generateProvider.getDescription());
    getTemplatePresentation().setIcon(icon);

    myProvider = generateProvider;
    
  }

  public GenerateDomElementAction(final GenerateDomElementProvider generateProvider) {
      this(generateProvider, null);
  }

  @Override
  @NotNull
  protected CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
        final Runnable runnable = new Runnable() {
          @Override
          public void run() {
            final DomElement element = myProvider.generate(project, editor, file);
            myProvider.navigate(element);
          }
        };
        
        if (GenerateDomElementAction.this.startInWriteAction()) {
          new WriteCommandAction(project, file) {
            @Override
            protected void run(@NotNull final Result result) throws Throwable {
              runnable.run();
            }
          }.execute();
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
  protected boolean isValidForFile(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    final DomElement element = DomUtil.getContextElement(editor);
    return element != null && myProvider.isAvailableForElement(element);
  }

  public GenerateDomElementProvider getProvider() {
    return myProvider;
  }
}
