// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemDescriptor;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class BaseFormInspection extends AbstractBaseJavaLocalInspectionTool implements FormInspectionTool {
  private final String myInspectionKey;

  public BaseFormInspection(@NonNls @NotNull String inspectionKey) {
    myInspectionKey = inspectionKey;
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
  }

  @Override
  public @NotNull
  @NonNls String getShortName() {
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
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!file.getFileType().equals(GuiFormFileType.INSTANCE)) {
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
  public ErrorInfo @Nullable [] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component) {
    FormEditorErrorCollector collector = new FormEditorErrorCollector(editor, component);
    checkComponentProperties(component.getModule(), component, collector);
    return collector.result();
  }

  protected abstract void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector);
}
