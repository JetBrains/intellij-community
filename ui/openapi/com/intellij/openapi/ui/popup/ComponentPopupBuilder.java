/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.openapi.ui.popup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public interface ComponentPopupBuilder {

  @NotNull
  ComponentPopupBuilder setTitle(String title);

  @NotNull
  ComponentPopupBuilder setResizable(final boolean forceResizable);

  @NotNull
  ComponentPopupBuilder setMovable(final boolean forceMovable);

  @NotNull
  ComponentPopupBuilder setRequestFocus(boolean requestFocus);

  @NotNull
  ComponentPopupBuilder setRequestFocusIfNotLookupOrSearch(Project project);

  @NotNull
  ComponentPopupBuilder setForceHeavyweight(boolean forceHeavyweight);

  @NotNull
  ComponentPopupBuilder setDimensionServiceKey(@NonNls final String dimensionServiceKey);

  @NotNull
  ComponentPopupBuilder setCancelCallback(final Computable<Boolean> shouldProceed);

  /**
   * @param updater recreates popup in accordance to selected (in lookup or Ctrl N) psiElement. Current popup will be closed automatically
   * @param project
   * @return this
   */
  @NotNull
  ComponentPopupBuilder setLookupAndSearchUpdater(final Condition<PsiElement> updater, Project project);

  @NotNull
  JBPopup createPopup();

}
