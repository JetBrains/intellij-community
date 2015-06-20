package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Creates methods for a builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class BuilderPreDefinedInnerClassFieldProcessor extends AbstractBuilderPreDefinedInnerClassProcessor {

  private final BuilderHandler builderHandler = new BuilderHandler();

  public BuilderPreDefinedInnerClassFieldProcessor() {
    this(Builder.class);
  }

  protected BuilderPreDefinedInnerClassFieldProcessor(@NotNull Class<? extends Annotation> builderClass) {
    super(builderClass, PsiField.class);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod, @NotNull PsiClass psiBuilderClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final Collection<PsiField> existedFields = PsiClassUtil.collectClassFieldsIntern(psiBuilderClass);
    if (null == psiParentMethod) {
      final Collection<PsiField> builderFields = builderHandler.getBuilderFields(psiParentClass, existedFields);
      target.addAll(builderHandler.generateFields(builderFields, psiBuilderClass, AccessorsInfo.build(psiParentClass)));
    } else {
      final Collection<PsiParameter> builderParameters = builderHandler.getBuilderParameters(psiParentMethod, existedFields);
      target.addAll(builderHandler.generateFields(builderParameters, psiBuilderClass, AccessorsInfo.EMPTY));
    }
  }
}
