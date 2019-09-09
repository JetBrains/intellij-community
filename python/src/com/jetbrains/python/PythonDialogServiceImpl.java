// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.jetbrains.python.documentation.PythonDocumentationProvider.DOCUMENTATION_CONFIGURABLE_ID;

public class PythonDialogServiceImpl extends PythonDialogService {
  @Override
  public int showChooseDialog(String message,
                              @Nls(capitalization = Nls.Capitalization.Title) String title,
                              String[] values,
                              String initialValue,
                              @Nullable Icon icon) {
    return MessagesService.getInstance().showChooseDialog(null, null, message, title, values, initialValue, icon);
  }

  @Override
  public void showNoExternalDocumentationDialog(Project project, QualifiedName qName) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final int rc = Messages.showOkCancelDialog(project,
                                                 "No external documentation URL configured for module " + qName.getComponents().get(0) +
                                                 ".\nWould you like to configure it now?",
                                                 "Python External Documentation",
                                                 Messages.getQuestionIcon());
      if (rc == Messages.OK) {
        ShowSettingsUtilImpl.showSettingsDialog(project, DOCUMENTATION_CONFIGURABLE_ID , "");
      }
    }, ModalityState.NON_MODAL);
  }
}
