/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.breadcrumbs;

import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.breadcrumbs.Crumb;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Malenkov
 */
final class PsiCrumb extends Crumb.Impl {
  private final PsiAnchor anchor;
  CrumbPresentation presentation;

  PsiCrumb(PsiElement element, BreadcrumbsProvider provider) {
    super(provider.getElementIcon(element), provider.getElementInfo(element), provider.getElementTooltip(element));
    anchor = PsiAnchor.create(element);
  }

  @Nullable
  static PsiElement getElement(Crumb crumb) {
    return crumb instanceof PsiCrumb ? ((PsiCrumb)crumb).anchor.retrieve() : null;
  }

  @Nullable
  static CrumbPresentation getPresentation(Crumb crumb) {
    return crumb instanceof PsiCrumb ? ((PsiCrumb)crumb).presentation : null;
  }
}
