package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.meta.model.YamlEnumType;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLValue;

@ApiStatus.Internal
public abstract class YamlDeprecatedValuesInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  protected @NotNull PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new YamlDeprecatedValuesInspectionBase.StructureChecker(holder, metaTypeProvider);
  }

  private static class StructureChecker extends SimpleYamlPsiVisitor {
    private final YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;

    StructureChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
      myProblemsHolder = problemsHolder;
      myMetaTypeProvider = metaTypeProvider;
    }

    @Override
    protected void visitYAMLKeyValue(@NotNull YAMLKeyValue keyValue) {
      YAMLValue yamlValue = keyValue.getValue();
      if (yamlValue == null) return;

      String yamlValueText = keyValue.getValueText();
      if (yamlValueText.isEmpty()) return;

      YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getValueMetaType(yamlValue);
      if (meta == null) return;

      YamlMetaType metaType = meta.getMetaType();
      if (metaType instanceof YamlEnumType enumValue) {
        if (enumValue.isLiteralDeprecated(yamlValueText)) {
          String msg = YAMLBundle.message("YamlDeprecatedKeysInspectionBase.deprecated.value", yamlValueText);
          myProblemsHolder.registerProblem(yamlValue, msg, ProblemHighlightType.LIKE_DEPRECATED);
        }
      }
    }
  }
}
