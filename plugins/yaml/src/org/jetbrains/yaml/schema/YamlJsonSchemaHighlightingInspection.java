// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.List;

public class YamlJsonSchemaHighlightingInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return YAMLBundle.message("inspections.schema.validation.name");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    PsiFile file = holder.getFile();
    if (!(file instanceof YAMLFile)) return PsiElementVisitor.EMPTY_VISITOR;
    List<YAMLDocument> documents = ((YAMLFile)file).getDocuments();
    if (documents.size() != 1) return PsiElementVisitor.EMPTY_VISITOR;

    PsiElement root = documents.get(0).getTopLevelValue();
    if (root == null) return PsiElementVisitor.EMPTY_VISITOR;

    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) return PsiElementVisitor.EMPTY_VISITOR;
    final JsonSchemaObject rootSchema = service.getSchemaObject(virtualFile);
    if (rootSchema == null) return PsiElementVisitor.EMPTY_VISITOR;

    JsonSchemaObject object = service.getSchemaObject(virtualFile);
    if (object == null) return PsiElementVisitor.EMPTY_VISITOR;
    return new YamlPsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element != root) return;
        final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, object);
        if (walker == null) return;

        new JsonSchemaComplianceChecker(object, holder, walker, session, "Schema validation: ").annotate(element);
      }
    };
  }
}
