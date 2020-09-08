// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions.validate;

import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class ValidateXmlAction extends AnAction {
  private static final Key<String> runningValidationKey = Key.create("xml.running.validation.indicator");

  public ValidateXmlAction() {
  }

  private ValidateXmlHandler getHandler(final @NotNull PsiFile file) {
    for (ValidateXmlHandler handler : ValidateXmlHandler.EP_NAME.getExtensionList()) {
      if (handler.isAvailable((XmlFile)file)) {
        return handler;
      }
    }
    ValidateXmlActionHandler handler = new ValidateXmlActionHandler(true);
    handler.setErrorReporter(new StdErrorReporter(handler, file, () -> doRunAction(file)));
    return handler;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (psiFile != null && psiFile.getVirtualFile() != null) {
      doRunAction(psiFile);
    }
  }

  private void doRunAction(final @NotNull PsiFile psiFile) {

    CommandProcessor.getInstance().executeCommand(
      psiFile.getProject(), () -> {
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

  private @Command String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text != null ? text : "";
  }

  @Override
  public void update(@NotNull AnActionEvent event) {

    Presentation presentation = event.getPresentation();
    PsiElement psiElement = event.getData(CommonDataKeys.PSI_FILE);

    boolean visible = psiElement instanceof XmlFile;
    presentation.setVisible(visible);
    boolean enabled = psiElement instanceof XmlFile;

    if (enabled) {
      final PsiFile containingFile = psiElement.getContainingFile();

      if (containingFile != null &&
          containingFile.getVirtualFile() != null &&
          (containingFile.getFileType() == XmlFileType.INSTANCE ||
           containingFile.getFileType() == XHtmlFileType.INSTANCE
          )) {
        enabled = containingFile.getUserData(runningValidationKey) == null;
      }
      else {
        enabled = false;
      }
    }

    presentation.setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
  }
}
