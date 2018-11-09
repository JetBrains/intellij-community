// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.quickFixes;

import com.intellij.psi.PsiClass;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.CustomCreateProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class GenerateCreateComponentsFix extends QuickFix {
  private final PsiClass myClass;

  public GenerateCreateComponentsFix(@NotNull final GuiEditor editor, PsiClass aClass) {
    super(editor, UIDesignerBundle.message("quickfix.generate.custom.create"), null);
    myClass = aClass;
  }

  @Override
  public void run() {
    CustomCreateProperty.generateCreateComponentsMethod(myClass);
  }
}
