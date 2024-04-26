// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;

import java.util.Collection;

public abstract class YamlJsonSchemaInspectionBase extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    PsiFile file = holder.getFile();
    if (!(file instanceof YAMLFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    Collection<PsiElement> roots = YamlJsonPsiWalker.INSTANCE.getRoots(file);
    if (roots.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final JsonSchemaObject rootSchema = service.getSchemaObject(file);
    if (rootSchema == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return doBuildVisitor(holder, session, roots, rootSchema);
  }

  protected abstract PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder,
                                                      @NotNull LocalInspectionToolSession session,
                                                      Collection<PsiElement> roots,
                                                      JsonSchemaObject object);
}
