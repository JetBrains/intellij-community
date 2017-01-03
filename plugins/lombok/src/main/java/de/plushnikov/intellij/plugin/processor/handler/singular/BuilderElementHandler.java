package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface BuilderElementHandler {
  void addBuilderField(@NotNull List<PsiField> fields, @NotNull PsiVariable psiVariable, @NotNull PsiClass innerClass, @NotNull AccessorsInfo accessorsInfo, PsiSubstitutor substitutor);

  void addBuilderMethod(@NotNull List<PsiMethod> methods, @NotNull PsiVariable psiVariable, @NotNull String fieldName, @NotNull PsiClass innerClass, boolean fluentBuilder, PsiType returnType, String singularName, PsiSubstitutor builderSubstitutor);

  String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName);

  void appendBuildPrepare(@NotNull StringBuilder buildMethodParameters, @NotNull PsiVariable psiVariable, @NotNull String fieldName);

  void appendBuildCall(@NotNull StringBuilder buildMethodParameters, @NotNull String fieldName);
}
