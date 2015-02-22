package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
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
  protected void generatePsiElements(@NotNull PsiClass psiParentClass, @NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.addAll(builderHandler.createFields(psiParentClass, PsiClassUtil.collectClassFieldsIntern(psiClass)));
  }
}
