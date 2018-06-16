// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaDocumentationProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class YamlJsonSchemaDocumentationProvider extends DocumentationProviderEx {
    @Nullable
    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
      return findSchemaAndGenerateDoc(element, true);
    }

    @Nullable
    @Override
    public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
      return null;
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
      VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return null;
      JsonSchemaObject schemaObject = jsonSchemaService.getSchemaObject(virtualFile);
      if (schemaObject == null) return null;
      return JsonSchemaDocumentationProvider.generateDoc(element, schemaObject, preferShort);
    }

    @Nullable
    @Override
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
      return null;
    }

    @Nullable
    @Override
    public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
      return null;
    }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                  @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    if (service == null || service.getSchemaObject(file.getVirtualFile()) == null) return null;
    return contextElement;
  }
}
