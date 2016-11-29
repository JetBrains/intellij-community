/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private final PsiFile myFormPsiFile;

  public FormEditorErrorCollector(final GuiEditor editor, final RadComponent component) {
    myEditor = editor;
    myComponent = component;

    myFormPsiFile = PsiManager.getInstance(editor.getProject()).findFile(editor.getFile());
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(editor.getProject());
    myProfile = profileManager.getCurrentProfile();
  }

  public ErrorInfo[] result() {
    return myResults == null ? null : myResults.toArray(new ErrorInfo[myResults.size()]);
  }

  public void addError(@NotNull final String inspectionId, final IComponent component, @Nullable IProperty prop,
                       @NotNull String errorMessage,
                       EditorQuickFixProvider... editorQuickFixProviders) {
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
                                              quickFixes.toArray(new QuickFix[quickFixes.size()]));
    errorInfo.setInspectionId(inspectionId);
    myResults.add(errorInfo);
  }
}
