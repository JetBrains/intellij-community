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
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class BaseFormInspection extends BaseJavaLocalInspectionTool implements FormInspectionTool {
  private final String myInspectionKey;

  public BaseFormInspection(@NonNls @NotNull String inspectionKey) {
    myInspectionKey = inspectionKey;
  }

  @Override
  @Nls @NotNull
  public String getDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
  }

  @Override
  @NotNull @NonNls public String getShortName() {
    return myInspectionKey;
  }

  @Override public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean isActive(PsiElement psiRoot) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiRoot.getProject()).getCurrentProfile();
    HighlightDisplayKey key = HighlightDisplayKey.find(myInspectionKey);
    return key != null && profile.isToolEnabled(key, psiRoot);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!file.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      return null;
    }
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, file.getProject());
    if (module == null) {
      return null;
    }

    final LwRootContainer rootContainer;
    try {
      rootContainer = Utils.getRootContainer(file.getText(), new PsiPropertiesProvider(module));
    }
    catch (Exception e) {
      return null;
    }

    if (ErrorAnalyzer.isSuppressed(rootContainer, this, null)) {
      return null;
    }
    final FormFileErrorCollector collector = new FormFileErrorCollector(file, manager, isOnTheFly);
    startCheckForm(rootContainer);
    FormEditingUtil.iterate(rootContainer, component -> {
      if (!ErrorAnalyzer.isSuppressed(rootContainer, this, component.getId())) {
        checkComponentProperties(module, component, collector);
      }
      return true;
    });
    doneCheckForm(rootContainer);
    return collector.result();
  }

  @Override
  public void startCheckForm(IRootContainer rootContainer) {
  }

  @Override
  public void doneCheckForm(IRootContainer rootContainer) {
  }

  @Override
  @Nullable
  public ErrorInfo[] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component) {
    FormEditorErrorCollector collector = new FormEditorErrorCollector(editor, component);
    checkComponentProperties(component.getModule(), component, collector);
    return collector.result();
  }

  protected abstract void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector);
}
