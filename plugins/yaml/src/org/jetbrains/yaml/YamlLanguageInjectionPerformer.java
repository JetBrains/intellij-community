// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionPerformer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalar;

public class YamlLanguageInjectionPerformer implements LanguageInjectionPerformer {

  @Override
  public boolean isPrimary() {
    return true;
  }

  @Override
  public boolean performInjection(@NotNull MultiHostRegistrar registrar,
                                  @NotNull Injection injection,
                                  @NotNull PsiElement context) {
    if (!(context instanceof YAMLScalar)) return false;

    Language language = Language.findLanguageByID(injection.getInjectedLanguageId());
    if (language == null) return false;

    registrar.startInjecting(language);
    TextRange textRange = ElementManipulators.getValueTextRange(context);
    registrar.addPlace(injection.getPrefix(), injection.getSuffix(), (PsiLanguageInjectionHost)context, textRange);
    registrar.doneInjecting();
    return true;
  }
}
