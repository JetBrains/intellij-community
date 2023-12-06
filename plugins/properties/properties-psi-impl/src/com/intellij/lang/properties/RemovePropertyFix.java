// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.lang.properties.psi.Property;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class RemovePropertyFix extends PsiUpdateModCommandAction<Property> {
  RemovePropertyFix(@NotNull final Property origProperty) {
    super(origProperty);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PropertiesBundle.message("remove.property.intention.text");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull Property property, @NotNull ModPsiUpdater updater) {
    property.delete();
  }
}
