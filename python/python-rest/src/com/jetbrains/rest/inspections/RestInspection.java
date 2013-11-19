/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.inspections;

import com.intellij.codeInspection.CustomSuppressableInspectionTool;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.psi.PsiElement;
import com.jetbrains.rest.RestBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public abstract class RestInspection extends LocalInspectionTool implements CustomSuppressableInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return RestBundle.message("INSP.GROUP.rest");
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
  public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
    return null;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    return false;
  }
}
