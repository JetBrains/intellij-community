// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  protected void doInvoke(@NotNull final PsiFile file, final int offset, @NotNull final String uri, final Editor editor) throws IncorrectOperationException {
    final MapExternalResourceDialog dialog = new MapExternalResourceDialog(uri, file.getProject(), file, null);
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
