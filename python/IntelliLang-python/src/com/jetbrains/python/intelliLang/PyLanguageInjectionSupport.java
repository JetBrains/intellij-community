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
package com.jetbrains.python.intelliLang;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.patterns.PythonPatterns;
import com.jetbrains.python.psi.PyElement;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  @NonNls private static final String SUPPORT_ID = "python";

  @NotNull
  @Override
  public String getId() {
    return SUPPORT_ID;
  }

  @NotNull
  @Override
  public Class[] getPatternClasses() {
    return new Class[] { PythonPatterns.class };
  }

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return host instanceof PyElement;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return "reference.settings.language.injection.generic.python";
  }

  @Nullable
  @Override
  public BaseInjection findCommentInjection(@NotNull PsiElement host, @Nullable Ref<PsiElement> commentRef) {
    return super.findCommentInjection(host, commentRef);
  }
}
