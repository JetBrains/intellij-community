package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface BuilderElementHandler {

  String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName);

  default String renderBuildPrepare(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    return "";
  }

  Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info);

  Collection<PsiMethod> renderBuilderMethod(@NotNull BuilderInfo info);

  List<String> getBuilderMethodNames(@NotNull String newName, @Nullable PsiAnnotation singularAnnotation);
}
