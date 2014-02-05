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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.DependentNSReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
abstract class BaseExtResourceAction extends BaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    String uri = findUri(file, offset);
    if (uri == null || !isAcceptableUri(uri)) return false;

    setText(XmlBundle.message(getQuickFixKeyId()));
    return true;
  }

  protected boolean isAcceptableUri(final String uri) {
    return true;
  }

  protected abstract String getQuickFixKeyId();

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlBundle.message(getQuickFixKeyId());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    int offset = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final String uri = findUri(file, offset);
    if (uri == null) return;

    doInvoke(file, offset, uri, editor);
  }

  protected abstract void doInvoke(@NotNull final PsiFile file, final int offset, @NotNull final String uri, final Editor editor)
    throws IncorrectOperationException;

  @Nullable
  public static String findUri(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null ||
        element instanceof PsiWhiteSpace ) {
      return null;
    }

    PsiReference currentRef = file.getViewProvider().findReferenceAt(offset, file.getLanguage());
    if (currentRef == null) currentRef = file.getViewProvider().findReferenceAt(offset);
    if (currentRef instanceof URLReference ||
        currentRef instanceof DependentNSReference) {
      return currentRef.getCanonicalText();
    }
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ManuallySetupExtResourceAction;
  }
}