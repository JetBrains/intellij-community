// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;

public class YamlJsonSchemaCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final JsonSchemaService jsonSchemaService = JsonSchemaService.Impl.get(position.getProject());
    JsonSchemaObject object = jsonSchemaService.getSchemaObject(parameters.getOriginalFile().getVirtualFile());
    if (object != null) {
      JsonSchemaCompletionContributor.doCompletion(parameters, result, object);
    }
  }
}
