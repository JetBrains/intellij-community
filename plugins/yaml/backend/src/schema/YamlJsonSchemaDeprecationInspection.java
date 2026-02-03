package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.MatchResult;
import com.jetbrains.jsonSchema.impl.light.nodes.RootJsonSchemaObjectBackedByJackson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.Collection;

public class YamlJsonSchemaDeprecationInspection extends YamlJsonSchemaInspectionBase {

  @Override
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder,
                                             @NotNull LocalInspectionToolSession session,
                                             Collection<PsiElement> roots,
                                             JsonSchemaObject schema) {
    if (schema == null || (schema instanceof RootJsonSchemaObjectBackedByJackson rootSchema && !rootSchema.checkHasDeprecations())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    PsiElement sampleElement = roots.iterator().next();
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(sampleElement, schema);
    if (walker == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    Project project = sampleElement.getProject();
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

        final MatchResult result = new JsonSchemaResolver(project, schema, position, walker.createValueAdapter(key)).detailedResolve();
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