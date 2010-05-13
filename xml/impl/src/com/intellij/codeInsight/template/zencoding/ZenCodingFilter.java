/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface ZenCodingFilter {
  ExtensionPointName<ZenCodingFilter> EP_NAME = new ExtensionPointName<ZenCodingFilter>("com.intellij.xml.zenCodingFilter");

  @NotNull
  String toString(@NotNull TemplateToken token, @NotNull PsiElement context);

  @NotNull
  String buildAttributesString(@NotNull List<Pair<String, String>> attribute2value, int numberInIteration);

  boolean isMyContext(@NotNull PsiElement context);

  @Nullable
  String getSuffix();

  boolean isDefaultFilter();
}
