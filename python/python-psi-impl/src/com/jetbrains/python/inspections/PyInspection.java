/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
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
    if (PythonSdkUtil.findPythonSdk(topLevelFile) != null) {
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
