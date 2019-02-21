package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.MatchResult;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

public class YamlJsonSchemaDeprecationInspection extends YamlJsonSchemaInspectionBase {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return YAMLBundle.message("inspections.schema.deprecation.name");
  }

  @Override
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder,
                                             @NotNull LocalInspectionToolSession session,
                                             PsiElement root,
                                             JsonSchemaObject schema) {
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(root, schema);
    if (walker == null || schema == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new YamlPsiElementVisitor() {
      @Override
      public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
        annotate(keyValue);
        super.visitKeyValue(keyValue);
      }

      private void annotate(@NotNull YAMLKeyValue keyValue) {
        PsiElement key = keyValue.getKey();
        if (key == null) {
          return;
        }
        JsonPointerPosition position = walker.findPosition(keyValue, true);
        if (position == null) {
          return;
        }

        final MatchResult result = new JsonSchemaResolver(schema, false, position).detailedResolve();
        for (JsonSchemaObject object : result.mySchemas) {
          String message = object.getDeprecationMessage();
          if (message != null) {
            holder.registerProblem(key, YAMLBundle.message("inspections.schema.deprecation.text", keyValue.getName(), message));
            return;
          }
        }
      }
    };
  }
}