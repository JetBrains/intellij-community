package com.intellij.uiDesigner.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FormEditorErrorCollector extends FormErrorCollector {
  private final GuiEditor myEditor;
  private final RadComponent myComponent;
  private List<ErrorInfo> myResults = null;
  private final InspectionProfile myProfile;
  private PsiFile myFormPsiFile;

  public FormEditorErrorCollector(final GuiEditor editor, final RadComponent component) {
    myEditor = editor;
    myComponent = component;

    myFormPsiFile = PsiManager.getInstance(editor.getProject()).findFile(editor.getFile());
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(editor.getProject());
    myProfile = profileManager.getInspectionProfile();
  }

  public ErrorInfo[] result() {
    return myResults == null ? null : myResults.toArray(new ErrorInfo[myResults.size()]);
  }

  public void addError(final String inspectionId, final IComponent component, @Nullable IProperty prop,
                       @NotNull String errorMessage,
                       @Nullable EditorQuickFixProvider editorQuickFixProvider) {
    if (myResults == null) {
      myResults = new ArrayList<ErrorInfo>();
    }
    QuickFix[] quickFixes = QuickFix.EMPTY_ARRAY;
    if (editorQuickFixProvider != null) {
      quickFixes = new QuickFix[1];
      quickFixes [0] = editorQuickFixProvider.createQuickFix(myEditor, myComponent);
    }

    final ErrorInfo errorInfo = new ErrorInfo(myComponent, prop == null ? null : prop.getName(), errorMessage,
                                              myProfile.getErrorLevel(HighlightDisplayKey.find(inspectionId), myFormPsiFile), quickFixes);
    errorInfo.setInspectionId(inspectionId);
    myResults.add(errorInfo);
  }
}
