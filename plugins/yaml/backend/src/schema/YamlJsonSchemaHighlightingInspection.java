// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.json.JsonBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.Collection;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class YamlJsonSchemaHighlightingInspection extends YamlJsonSchemaInspectionBase {
  public boolean myCaseInsensitiveEnum = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myCaseInsensitiveEnum", JsonBundle.message("json.schema.inspection.case.insensitive.enum")));
  }

  @Override
  protected @NotNull PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder,
                                                      @NotNull LocalInspectionToolSession session,
                                                      Collection<PsiElement> roots,
                                                      JsonSchemaObject object) {
    JsonComplianceCheckerOptions options = new JsonComplianceCheckerOptions(myCaseInsensitiveEnum);
    return new YamlPsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!roots.contains(element)) return;
        final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, object);
        if (walker == null) return;
        String prefix = YAMLBundle.message("inspections.schema.validation.prefix") + " ";
        new JsonSchemaComplianceChecker(object, holder, walker, session, options,prefix).annotate(element);
      }
    };
  }
}
