// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;

public class YamlJsonLikePsiWalkerFactory implements JsonLikePsiWalkerFactory {
  @Override
  public boolean handles(@NotNull PsiElement element) {
    return element.getContainingFile() instanceof YAMLFile;
  }

  @NotNull
  @Override
  public JsonLikePsiWalker create(@NotNull JsonSchemaObject schemaObject) {
    return new YamlJsonPsiWalker();
  }
}
