// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.List;

public abstract class YamlJsonSchemaInspectionBase extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    PsiFile file = holder.getFile();
    if (!(file instanceof YAMLFile)) return PsiElementVisitor.EMPTY_VISITOR;
    List<YAMLDocument> documents = ((YAMLFile)file).getDocuments();
    if (documents.size() != 1) return PsiElementVisitor.EMPTY_VISITOR;

    YAMLDocument document = documents.get(0);
    YAMLValue topLevelValue = document.getTopLevelValue();
    PsiElement root = topLevelValue == null ? document : topLevelValue;
    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) return PsiElementVisitor.EMPTY_VISITOR;
    final JsonSchemaObject rootSchema = service.getSchemaObject(virtualFile);
    if (rootSchema == null) return PsiElementVisitor.EMPTY_VISITOR;

    JsonSchemaObject object = service.getSchemaObject(virtualFile);
    if (object == null) return PsiElementVisitor.EMPTY_VISITOR;
    return doBuildVisitor(holder, session, root, object);
  }

  protected abstract PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder,
                                                      @NotNull LocalInspectionToolSession session,
                                                      PsiElement root,
                                                      JsonSchemaObject object);
}
