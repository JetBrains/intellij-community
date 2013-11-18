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
package com.jetbrains.python.intelliLang;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.patterns.PythonPatterns;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  @Override
  public boolean useDefaultInjector(PsiLanguageInjectionHost host) {
    return true;
  }

  @Override
  public BaseInjection createInjection(Element element) {
    // This is how DefaultLanguageInjector gets its injection ranges
    return new BaseInjection(getId()) {
      @NotNull
      @Override
      public List<TextRange> getInjectedArea(PsiElement element) {
        if (element instanceof PyStringLiteralExpression) {
          return ((PyStringLiteralExpression)element).getStringValueTextRanges();
        }
        return super.getInjectedArea(element);
      }
    };
  }

  @Override
  public boolean addInjectionInPlace(Language language, PsiLanguageInjectionHost psiElement) {
    // XXX: Disable temporary injections via intention actions for Python elements, since TemporaryPlacesInjector cannot handle elements
    // with multiple injection text ranges (PY-10691)
    return true;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return "reference.settings.language.injection.generic.python";
  }
}
