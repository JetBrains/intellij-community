package com.intellij.uiDesigner.inspections;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author yole
 */
public interface EditorQuickFixProvider {
  QuickFix createQuickFix(GuiEditor editor, RadComponent component);
}
