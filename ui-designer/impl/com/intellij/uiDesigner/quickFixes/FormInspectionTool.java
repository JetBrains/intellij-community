package com.intellij.uiDesigner.quickFixes;

import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.08.2005
 * Time: 17:16:32
 * To change this template use File | Settings | File Templates.
 */
public interface FormInspectionTool {
  @Nullable
  ErrorInfo[] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component);

  boolean isActive(PsiElement psiRoot);
}
