// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.inspections;

import com.intellij.psi.PsiElement;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;


public interface FormInspectionTool {
  ExtensionPointName<FormInspectionTool> EP_NAME = new ExtensionPointName<>("com.intellij.uiDesigner.formInspectionTool");

  @NonNls
  String getShortName();
  void startCheckForm(IRootContainer radRootContainer);
  void doneCheckForm(IRootContainer radRootContainer);

  ErrorInfo @Nullable [] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component);

  boolean isActive(PsiElement psiRoot);

}
