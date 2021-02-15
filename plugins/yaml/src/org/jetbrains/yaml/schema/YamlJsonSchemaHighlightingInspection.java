// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.json.JsonBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import javax.swing.*;
import java.util.Collection;

public class YamlJsonSchemaHighlightingInspection extends YamlJsonSchemaInspectionBase {
  public boolean myCaseInsensitiveEnum = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(JsonBundle.message("json.schema.inspection.case.insensitive.enum"), "myCaseInsensitiveEnum");
    return optionsPanel;
  }

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder,
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
