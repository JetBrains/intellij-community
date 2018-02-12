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
package com.intellij.uiDesigner.quickFixes;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeFieldTypeFix extends QuickFix {
  private final PsiField myField;
  private final PsiType myNewType;

  public ChangeFieldTypeFix(GuiEditor uiEditor, PsiField field, PsiType uiComponentType) {
    super(uiEditor, gettext(field, uiComponentType), null);
    myField = field;
    myNewType = uiComponentType;
  }

  private static String gettext(PsiField field, PsiType uiComponentType) {
    return UIDesignerBundle.message("action.change.field.type",
                                         field.getName(), field.getType().getCanonicalText(), uiComponentType.getCanonicalText());
  }

  public void run() {
    final PsiFile psiFile = myField.getContainingFile();
    if (psiFile == null) return;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(psiFile)) return;
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myField.getProject(), () -> {
      try {
        final PsiManager manager = myField.getManager();
        myField.getTypeElement().replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(myNewType));
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showErrorDialog(myEditor, UIDesignerBundle.message("error.cannot.change.field.type", myField.getName(), e.getMessage()),
                                       CommonBundle.getErrorTitle()));
      }
    }, getName(), null));
  }
}
