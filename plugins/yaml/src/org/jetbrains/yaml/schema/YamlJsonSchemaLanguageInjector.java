// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.jsonSchema.impl.JsonSchemaBasedLanguageInjector;
import com.jetbrains.jsonSchema.impl.JsonSchemaInjectorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.Collections;
import java.util.List;

public class YamlJsonSchemaLanguageInjector implements MultiHostInjector {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!(context instanceof YAMLScalar)) {
      return;
    }
    JsonSchemaInjectorBase.InjectedLanguageData language = JsonSchemaBasedLanguageInjector.getLanguageToInject(context, true);
    if (language != null) {
      registrar.startInjecting(language.language);
      registrar.addPlace(language.prefix, language.postfix, (PsiLanguageInjectionHost)context, context.getTextRange().shiftLeft(context.getTextOffset()));
      registrar.doneInjecting();
    }
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(YAMLScalar.class);
  }
}
