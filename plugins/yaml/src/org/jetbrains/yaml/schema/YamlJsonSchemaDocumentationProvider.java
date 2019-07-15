// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaDocumentationProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class YamlJsonSchemaDocumentationProvider extends DocumentationProviderEx {
  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return findSchemaAndGenerateDoc(element, true);
  }

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    return findSchemaAndGenerateDoc(element, false);
  }

  @Nullable
  private static String findSchemaAndGenerateDoc(PsiElement element, final boolean preferShort) {
    final JsonSchemaService jsonSchemaService = JsonSchemaService.Impl.get(element.getProject());
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;
    JsonSchemaObject schemaObject = jsonSchemaService.getSchemaObject(containingFile);
    if (schemaObject == null) return null;
    return JsonSchemaDocumentationProvider.generateDoc(element, schemaObject, preferShort, null);
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                  @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    if (service == null || service.getSchemaObject(file) == null) return null;
    return contextElement;
  }
}
