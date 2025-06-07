// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.MapExternalResourceDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ManuallySetupExtResourceAction extends BaseExtResourceAction {

  private static final String KEY = "xml.intention.manually.setup.external.resource";

  @Override
  protected String getQuickFixKeyId() {
    return KEY;
  }

  @Override
  protected void doInvoke(final @NotNull PsiFile psiFile, final int offset, final @NotNull String uri, final Editor editor) throws IncorrectOperationException {
    final MapExternalResourceDialog dialog = new MapExternalResourceDialog(uri, psiFile.getProject(), psiFile, null);
    if (dialog.showAndGet()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        String location = dialog.getResourceLocation();
        ExternalResourceManager.getInstance().addResource(dialog.getUri(), location);
      });
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
