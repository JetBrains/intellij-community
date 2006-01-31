package com.intellij.uiDesigner.inspections;

import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IProperty;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FormEditorErrorCollector extends FormErrorCollector {
  private GuiEditor myEditor;
  private RadComponent myComponent;
  private List<ErrorInfo> myResults = null;

  public FormEditorErrorCollector(final GuiEditor editor, final RadComponent component) {
    myEditor = editor;
    myComponent = component;
  }

  public ErrorInfo[] result() {
    return myResults == null ? null : myResults.toArray(new ErrorInfo[myResults.size()]);
  }

  public void addError(@Nullable IProperty prop, @NotNull String errorMessage,
                       @Nullable EditorQuickFixProvider editorQuickFixProvider) {
    if (myResults == null) {
      myResults = new ArrayList<ErrorInfo>();
    }
    QuickFix[] quickFixes = QuickFix.EMPTY_ARRAY;
    if (editorQuickFixProvider != null) {
      quickFixes = new QuickFix[1];
      quickFixes [0] = editorQuickFixProvider.createQuickFix(myEditor, myComponent);
    }
    myResults.add(new ErrorInfo(prop == null ? null : prop.getName(),
                                errorMessage, quickFixes));
  }
}
