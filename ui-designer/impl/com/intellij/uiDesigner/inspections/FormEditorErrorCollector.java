package com.intellij.uiDesigner.inspections;

import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
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
  private InspectionProfileImpl myProfile;

  public FormEditorErrorCollector(final GuiEditor editor, final RadComponent component) {
    myEditor = editor;
    myComponent = component;

    final PsiFile formPsiFile = PsiManager.getInstance(editor.getProject()).findFile(editor.getFile());
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(editor.getProject());
    myProfile = profileManager.getProfile(formPsiFile);
  }

  public ErrorInfo[] result() {
    return myResults == null ? null : myResults.toArray(new ErrorInfo[myResults.size()]);
  }

  public void addError(final String inspectionId,
                       @Nullable IProperty prop,
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

    myResults.add(new ErrorInfo(prop == null ? null : prop.getName(),
                                errorMessage,
                                myProfile.getErrorLevel(HighlightDisplayKey.find(inspectionId)),
                                quickFixes));
  }
}
