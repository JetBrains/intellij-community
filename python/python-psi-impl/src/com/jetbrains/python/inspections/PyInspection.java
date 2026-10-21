// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModelKt;
import com.intellij.python.sdk.backend.evolution.EvoPyProject;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyInspection extends LocalInspectionTool {
  @Pattern(VALID_ID_PATTERN)
  @Override
  public @NotNull String getID() {
    //noinspection PatternValidation
    return getShortName(super.getID());
  }

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return PyPsiBundle.message("INSP.GROUP.python");
  }

  @Override
  public @NotNull String getShortName() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(this.getClass())) {
      return true;
    }
    if (file instanceof PyFileImpl) {
      final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
      if (isInjectedIntoNonPythonHost(injectedLanguageManager, file) && !hasPythonRuntime(injectedLanguageManager, file)) {
        return true;
      }
    }
    return isSuppressForCodeFragment(element) || super.isSuppressedFor(element);
  }

  private boolean isSuppressForCodeFragment(@Nullable PsiElement element) {
    return isSuppressForCodeFragment() && PsiTreeUtil.getParentOfType(element, PyExpressionCodeFragment.class) != null;
  }

  protected boolean isSuppressForCodeFragment() {
    return false;
  }

  private static boolean hasPythonRuntime(@NotNull InjectedLanguageManager injectedLanguageManager, @NotNull PsiFile containingFile) {
    final PsiFile topLevelFile = injectedLanguageManager.getTopLevelFile(containingFile);
    // An inspection cannot suspend, so it peeks at the snapshot. Each new snapshot restarts the analysis.
    final EvoPyProject evoPyProject = EvoPyProjectModelKt.findEvoPyProjectIfReady(topLevelFile, false);
    if (evoPyProject != null && evoPyProject.getInterpreter() != null) {
      return true;
    }

    final PythonRuntimeService pythonRuntimeService = PythonRuntimeService.getInstance();
    return pythonRuntimeService.isInScratchFile(topLevelFile) || pythonRuntimeService.isExternallyIndexedFile(topLevelFile);
  }

  private static boolean isInjectedIntoNonPythonHost(@NotNull InjectedLanguageManager injectedLanguageManager,
                                                     @NotNull PsiFile containingFile) {
    final PsiElement injectionHost = injectedLanguageManager.getInjectionHost(containingFile);
    if (injectionHost == null) {
      return false;
    }

    final PsiFile topLevelFile = injectedLanguageManager.getTopLevelFile(containingFile);
    if (topLevelFile == containingFile) {
      return false;
    }

    return getBaseLanguageOrLanguage(topLevelFile) != getBaseLanguageOrLanguage(containingFile);
  }

  private static @NotNull Language getBaseLanguageOrLanguage(@NotNull PsiFile file) {
    final Language language = file.getLanguage();
    final Language baseLanguage = language.getBaseLanguage();
    return baseLanguage != null ? baseLanguage : language;
  }
}
