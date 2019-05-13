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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyInspection extends LocalInspectionTool {
  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    //noinspection PatternValidation
    return getShortName(super.getID());
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @NotNull
  @Override
  public String getShortName() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(this.getClass())) {
      return true;
    }
    return isSuppressForCodeFragment(element) || super.isSuppressedFor(element);
  }

  private boolean isSuppressForCodeFragment(@Nullable PsiElement element) {
    return isSuppressForCodeFragment() && PsiTreeUtil.getParentOfType(element, PyExpressionCodeFragment.class) != null;
  }

  protected boolean isSuppressForCodeFragment() {
    return false;
  }
}
