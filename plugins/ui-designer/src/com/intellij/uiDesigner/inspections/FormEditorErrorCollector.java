// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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


public class FormEditorErrorCollector extends FormErrorCollector {
  private final GuiEditor myEditor;
  private final @NotNull RadComponent myComponent;
  private List<ErrorInfo> myResults;
  private final InspectionProfile myProfile;
  private final PsiFile myFormPsiFile;

  FormEditorErrorCollector(final GuiEditor editor, @NotNull RadComponent component) {
    myEditor = editor;
    myComponent = component;

    myFormPsiFile = PsiManager.getInstance(editor.getProject()).findFile(editor.getFile());
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(editor.getProject());
    myProfile = profileManager.getCurrentProfile();
  }

  public ErrorInfo[] result() {
    return myResults == null ? null : myResults.toArray(ErrorInfo.EMPTY_ARRAY);
  }

  @Override
  public void addError(final @NotNull String inspectionId, final @NotNull IComponent component, @Nullable IProperty prop,
                       @NotNull String errorMessage,
                       EditorQuickFixProvider @NotNull ... editorQuickFixProviders) {
    if (myResults == null) {
      myResults = new ArrayList<>();
    }
    List<QuickFix> quickFixes = new ArrayList<>();
    for (EditorQuickFixProvider provider : editorQuickFixProviders) {
      if (provider != null) {
        quickFixes.add(provider.createQuickFix(myEditor, myComponent));
      }
    }

    final ErrorInfo errorInfo = new ErrorInfo(myComponent, prop == null ? null : prop.getName(), errorMessage,
                                              myProfile.getErrorLevel(HighlightDisplayKey.find(inspectionId), myFormPsiFile),
                                              quickFixes.toArray(QuickFix.EMPTY_ARRAY));
    errorInfo.setInspectionId(inspectionId);
    myResults.add(errorInfo);
  }
}
