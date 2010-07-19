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
package com.intellij.codeInsight.template.zencoding.filters;

import com.intellij.codeInsight.template.zencoding.tokens.TemplateToken;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ZenCodingGenerator {
  private static final ExtensionPointName<ZenCodingGenerator> EP_NAME = new ExtensionPointName<ZenCodingGenerator>("com.intellij.xml.zenCodingGenerator");

  @NotNull
  public abstract String toString(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context);

  public abstract boolean isMyContext(@NotNull PsiElement context);

  @Nullable
  public abstract String getSuffix();

  public abstract boolean isAppliedByDefault(@NotNull PsiElement context);

  public static List<ZenCodingGenerator> getInstances() {
    List<ZenCodingGenerator> generators = new ArrayList<ZenCodingGenerator>();
    Collections.addAll(generators, XmlZenCodingGeneratorImpl.INSTANCE);
    Collections.addAll(generators, EP_NAME.getExtensions());
    return generators;
  }
}
