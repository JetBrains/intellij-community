/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Jun 19, 2007
 * Time: 3:33:15 PM
 */
package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BreadcrumbsInfoProvider {
  public static final ExtensionPointName<BreadcrumbsInfoProvider> EP_NAME = ExtensionPointName.create("com.intellij.breadcrumbsInfoProvider");

  public abstract Language[] getLanguages();

  public abstract boolean acceptElement(@NotNull final PsiElement e);

  @Nullable
  public PsiElement getParent(@NotNull final PsiElement e) {
    return e.getParent();
  }

  @NotNull
  public abstract String getElementInfo(@NotNull final PsiElement e);

  @Nullable
  public abstract String getElementTooltip(@NotNull final PsiElement e);
}