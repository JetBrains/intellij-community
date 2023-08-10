// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionContributor;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.impl.JsonSchemaBasedLanguageInjector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLScalar;

public class YamlJsonSchemaLanguageInjector implements LanguageInjectionContributor {
  @Override
  public @Nullable Injection getInjection(@NotNull PsiElement context) {
    if (!(context instanceof YAMLScalar)) {
      return null;
    }
    return JsonSchemaBasedLanguageInjector.getLanguageToInject(context, true);
  }
}
