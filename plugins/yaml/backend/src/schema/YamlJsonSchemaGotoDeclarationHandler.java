// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class YamlJsonSchemaGotoDeclarationHandler implements GotoDeclarationHandler {
  @Override
  public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    boolean shouldSuppressNavigation =
      ContainerUtil.exists(JsonSchemaGotoDeclarationSuppressor.EP_NAME.getExtensionList(), it -> it.shouldSuppressGtd(sourceElement));
    if (shouldSuppressNavigation) return null;

    final IElementType elementType = PsiUtilCore.getElementType(sourceElement);
    if (elementType != YAMLTokenTypes.SCALAR_KEY) return null;
    final YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(sourceElement, YAMLKeyValue.class);
    // do not override injected references
    if (keyValue == null || keyValue.getKey() != sourceElement|| !ArrayUtil.isEmpty(keyValue.getReferences())) return null;
    final JsonSchemaService service = JsonSchemaService.Impl.get(keyValue.getProject());
    final PsiFile containingFile = keyValue.getContainingFile();
    final VirtualFile file = containingFile.getVirtualFile();
    if (file == null || !service.isApplicableToFile(file)) return null;
    final JsonPointerPosition steps = YamlJsonPsiWalker.INSTANCE.findPosition(keyValue, true);
    if (steps == null) return null;
    final JsonSchemaObject schemaObject = service.getSchemaObject(containingFile);
    if (schemaObject != null) {
      final PsiElement target = new JsonSchemaResolver(sourceElement.getProject(), schemaObject, steps, YamlJsonPsiWalker.INSTANCE.createValueAdapter(keyValue.getParent()))
      //final PsiElement target = new JsonSchemaResolver(sourceElement.getProject(), schemaObject, steps, YamlJsonPsiWalker.INSTANCE.getParentPropertyAdapter(keyValue).getNameValueAdapter())
        .findNavigationTarget(keyValue.getValue());
      if (target != null) {
        return new PsiElement[] {target};
      }
    }
    return null;
  }
}
