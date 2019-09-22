package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates fields for a @SuperBuilder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class SuperBuilderPreDefinedInnerClassFieldProcessor extends AbstractSuperBuilderPreDefinedInnerClassProcessor {

  public SuperBuilderPreDefinedInnerClassFieldProcessor(@NotNull SuperBuilderHandler builderHandler) {
    super(builderHandler, PsiField.class, SuperBuilder.class);
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElementsOfBaseBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    final Collection<String> existedFieldNames = PsiClassUtil.collectClassFieldsIntern(psiBuilderClass).stream()
      .map(PsiField::getName)
      .collect(Collectors.toSet());

    final List<BuilderInfo> builderInfos = builderHandler.createBuilderInfos(psiAnnotation, psiParentClass, null, psiBuilderClass);
    return builderInfos.stream()
      .filter(info -> info.notAlreadyExistingField(existedFieldNames))
      .map(BuilderInfo::renderBuilderFields)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElementsOfImplBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    // ImplBuilder doesn't contains any fields
    return Collections.emptyList();
  }
}
