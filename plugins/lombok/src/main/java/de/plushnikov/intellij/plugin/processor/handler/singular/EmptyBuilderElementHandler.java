package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.thirdparty.CapitalizationStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class EmptyBuilderElementHandler implements BuilderElementHandler {

  @Override
  public Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info) {
    return Collections.emptyList();
  }

  @Override
  public Collection<PsiMethod> renderBuilderMethod(@NotNull BuilderInfo info) {
    return Collections.emptyList();
  }

  @Override
  public String calcBuilderMethodName(@NotNull BuilderInfo info) {
    return "";
  }

  @Override
  public List<String> getBuilderMethodNames(@NotNull String newName, @Nullable PsiAnnotation singularAnnotation,
                                            CapitalizationStrategy capitalizationStrategy) {
    return Collections.emptyList();
  }

  @Override
  public String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    return psiFieldName;
  }

}
