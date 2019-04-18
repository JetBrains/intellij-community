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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import javax.swing.*;
import java.util.Collection;

public class YamlJsonSchemaHighlightingInspection extends YamlJsonSchemaInspectionBase {
  public boolean myCaseInsensitiveEnum = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return YAMLBundle.message("inspections.schema.validation.name");
  }

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
      public void visitElement(PsiElement element) {
        if (!roots.contains(element)) return;
        final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, object);
        if (walker == null) return;
        new JsonSchemaComplianceChecker(object, holder, walker, session, options, "Schema validation: ").annotate(element);
      }
    };
  }
}
