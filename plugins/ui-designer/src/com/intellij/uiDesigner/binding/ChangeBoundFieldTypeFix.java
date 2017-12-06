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
package com.intellij.uiDesigner.binding;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeBoundFieldTypeFix implements IntentionAction {
  private final PsiField myField;
  private final PsiType myTypeToSet;

  public ChangeBoundFieldTypeFix(PsiField field, PsiType typeToSet) {
    myField = field;
    myTypeToSet = typeToSet;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("uidesigner.change.bound.field.type");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    CommandProcessor.getInstance().executeCommand(myField.getProject(), () -> {
      try {
        final PsiManager manager = myField.getManager();
        myField.getTypeElement().replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(myTypeToSet));
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(myField.getProject(),
                                                                                   QuickFixBundle.message("cannot.change.field.exception", myField.getName(), e.getLocalizedMessage()),
                                                                                   CommonBundle.getErrorTitle()));
      }
    }, getText(), null);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
