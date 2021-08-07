// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public final class CopyPropertyKeyToClipboardIntention implements IntentionAction, LowPriorityAction {

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return PropertiesBundle.message("copy.property.key.to.clipboard.intention.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             Editor editor,
                             PsiFile file) {
    return file.getLanguage().isKindOf(PropertiesLanguage.INSTANCE) &&
           CopyPropertyValueToClipboardIntention.getProperty(editor, file) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Property property = CopyPropertyValueToClipboardIntention.getProperty(editor, file);
    if (property == null) return;
    final String key = property.getUnescapedKey();
    if (key == null) return;
    CopyPasteManager.getInstance().setContents(new StringSelection(key));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}