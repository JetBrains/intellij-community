// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class PopupQuickFix<T> extends QuickFix {
  public PopupQuickFix(@NotNull final GuiEditor editor, @NotNull final @IntentionName String name, @Nullable RadComponent component) {
    super(editor, name, component);
  }

  public abstract PopupStep<T> getPopupStep();
}
