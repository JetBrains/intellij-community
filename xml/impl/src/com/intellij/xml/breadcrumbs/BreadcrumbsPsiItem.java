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
package com.intellij.xml.breadcrumbs;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class BreadcrumbsPsiItem extends BreadcrumbsItem {
  private final PsiElement myElement;
  private final BreadcrumbsInfoProvider myProvider;
  private CrumbPresentation myPresentation;

  public BreadcrumbsPsiItem(@NotNull final PsiElement element, @NotNull final BreadcrumbsInfoProvider provider) {
    myElement = element;
    myProvider = provider;
  }

  public void setPresentation(CrumbPresentation presentation) {
    myPresentation = presentation;
  }

  @Override
  public String getDisplayText() {
    return isValid() ? myProvider.getElementInfo(myElement) : "INVALID";
  }

  @Override
  public String getTooltip() {
    final String s = isValid() ? myProvider.getElementTooltip(myElement) : "";
    return s == null ? "" : s;
  }

  @Override
  public CrumbPresentation getPresentation() {
    return myPresentation;
  }

  public boolean isValid() {
    return myElement != null && myElement.isValid();
  }

  public PsiElement getPsiElement() {
    return myElement;
  }
}
