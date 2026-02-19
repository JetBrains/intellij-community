// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLPsiElement;

public class YamlJsonLikePsiWalkerFactory implements JsonLikePsiWalkerFactory {
  @Override
  public boolean handles(@NotNull PsiElement element) {
    return element.getContainingFile() instanceof YAMLFile || element instanceof YAMLPsiElement;
  }

  @Override
  public @NotNull JsonLikePsiWalker create(@Nullable JsonSchemaObject schemaObject) {
    return YamlJsonPsiWalker.INSTANCE;
  }
}
