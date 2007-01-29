package com.intellij.uiDesigner.quickFixes;

import com.intellij.psi.PsiElement;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public interface FormInspectionTool {
  ExtensionPointName<FormInspectionTool> EP_NAME = new ExtensionPointName<FormInspectionTool>("com.intellij.uiDesigner.formInspectionTool");

  @NonNls
  String getShortName();
  void startCheckForm(IRootContainer radRootContainer);
  void doneCheckForm(IRootContainer radRootContainer);

  @Nullable
  ErrorInfo[] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component);

  boolean isActive(PsiElement psiRoot);

}
