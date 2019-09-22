package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates fields for a @Builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class BuilderPreDefinedInnerClassFieldProcessor extends AbstractBuilderPreDefinedInnerClassProcessor {

  public BuilderPreDefinedInnerClassFieldProcessor(@NotNull BuilderHandler builderHandler) {
    super(builderHandler, PsiField.class, Builder.class);
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElements(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    final Collection<String> existedFieldNames = PsiClassUtil.collectClassFieldsIntern(psiBuilderClass).stream()
      .map(PsiField::getName)
      .collect(Collectors.toSet());

    final List<BuilderInfo> builderInfos = builderHandler.createBuilderInfos(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass);
    return builderInfos.stream()
      .filter(info -> info.notAlreadyExistingField(existedFieldNames))
      .map(BuilderInfo::renderBuilderFields)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }
}
