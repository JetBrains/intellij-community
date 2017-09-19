/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xml.actions.validate;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class ValidateXmlAction extends AnAction {
  private static final Key<String> runningValidationKey = Key.create("xml.running.validation.indicator");

  public ValidateXmlAction() {
  }

  private ValidateXmlActionHandler getHandler(final @NotNull PsiFile file) {
    ValidateXmlActionHandler handler = new ValidateXmlActionHandler(true);
    handler.setErrorReporter(
      new StdErrorReporter(handler, file.getProject(),
                           () -> doRunAction(file)
      )
    );
    return handler;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (psiFile != null && psiFile.getVirtualFile() != null) {
      doRunAction(psiFile);
    }
  }

  private void doRunAction(final @NotNull PsiFile psiFile) {

    CommandProcessor.getInstance().executeCommand(psiFile.getProject(), () -> {
      final Runnable action = () -> {
        try {
          psiFile.putUserData(runningValidationKey, "");
          PsiDocumentManager.getInstance(psiFile.getProject()).commitAllDocuments();

          getHandler(psiFile).doValidate((XmlFile)psiFile);
        }
        finally {
          psiFile.putUserData(runningValidationKey, null);
        }
      };
      ApplicationManager.getApplication().runWriteAction(action);
    },
                                                  getCommandName(),
                                                  null
    );
  }

  private String getCommandName(){
    String text = getTemplatePresentation().getText();
    return text != null ? text : "";
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);

    Presentation presentation = event.getPresentation();
    PsiElement psiElement = CommonDataKeys.PSI_FILE.getData(event.getDataContext());

    boolean visible = psiElement instanceof XmlFile;
    presentation.setVisible(visible);
    boolean enabled = psiElement instanceof XmlFile;

    if (enabled) {
      final PsiFile containingFile = psiElement.getContainingFile();

      if (containingFile!=null &&
          containingFile.getVirtualFile() != null &&
          (containingFile.getFileType() == StdFileTypes.XML ||
           containingFile.getFileType() == StdFileTypes.XHTML
          )) {
        enabled = containingFile.getUserData(runningValidationKey) == null;
      } else {
        enabled = false;
      }
    }

    presentation.setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
  }
}
