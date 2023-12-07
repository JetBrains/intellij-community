// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.quickFixes;

import com.intellij.psi.PsiClass;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.CustomCreateProperty;
import org.jetbrains.annotations.NotNull;


public class GenerateCreateComponentsFix extends QuickFix {
  private final PsiClass myClass;

  public GenerateCreateComponentsFix(final @NotNull GuiEditor editor, PsiClass aClass) {
    super(editor, UIDesignerBundle.message("quickfix.generate.custom.create"), null);
    myClass = aClass;
  }

  @Override
  public void run() {
    CustomCreateProperty.generateCreateComponentsMethod(myClass);
  }
}
