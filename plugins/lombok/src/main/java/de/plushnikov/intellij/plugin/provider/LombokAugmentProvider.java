package de.plushnikov.intellij.plugin.provider;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.processor.method.ExtensionMethodsHelper;
import de.plushnikov.intellij.plugin.processor.modifier.ModifierProcessor;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger log = Logger.getInstance(LombokAugmentProvider.class.getName());

  private final ValProcessor valProcessor;
  private final Collection<ModifierProcessor> modifierProcessors;

  public LombokAugmentProvider() {
    log.debug("LombokAugmentProvider created");

    modifierProcessors = LombokProcessorManager.getLombokModifierProcessors();
    valProcessor = new ValProcessor();
  }

  @NotNull
  @Override
  protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
    // skip if no lombok library is present
    if (!LombokLibraryUtil.hasLombokLibrary(modifierList.getProject())) {
      return modifiers;
    }

    // make copy of original modifiers
    Set<String> result = new HashSet<>(modifiers);

    // Loop through all available processors and give all of them a chance to respond
    for (ModifierProcessor processor : modifierProcessors) {
      if (processor.isSupported(modifierList)) {
        processor.transformModifiers(modifierList, result);
      }
    }

    return result;
  }

  @Override
  public boolean canInferType(@NotNull PsiTypeElement typeElement) {
    return LombokLibraryUtil.hasLombokLibrary(typeElement.getProject()) && valProcessor.canInferType(typeElement);
  }

  /*
   * The final fields that are marked with Builder.Default contains only possible value
   * because user can set another value during the creation of the object.
   */
  //see de.plushnikov.intellij.plugin.inspection.DataFlowInspectionTest.testDefaultBuilderFinalValueInspectionIsAlwaysThat
  //see de.plushnikov.intellij.plugin.inspection.PointlessBooleanExpressionInspectionTest.testPointlessBooleanExpressionBuilderDefault
  @Override
  protected boolean fieldInitializerMightBeChanged(@NotNull PsiField field) {
    return PsiAnnotationSearchUtil.isAnnotatedWith(field, LombokClassNames.BUILDER_DEFAULT);
  }

  @Nullable
  @Override
  protected PsiType inferType(@NotNull PsiTypeElement typeElement) {
    return LombokLibraryUtil.hasLombokLibrary(typeElement.getProject()) ? valProcessor.inferType(typeElement) : null;
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                        @NotNull final Class<Psi> type) {
    return getAugments(element, type, null);
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                        @NotNull final Class<Psi> type,
                                                        @Nullable String nameHint) {
    final List<Psi> emptyResult = Collections.emptyList();
    if ((type != PsiClass.class && type != PsiField.class && type != PsiMethod.class) || !(element instanceof PsiExtensibleClass)) {
      return emptyResult;
    }

    final PsiClass psiClass = (PsiClass) element;
    if (!psiClass.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      return emptyResult;
    }
    // Skip processing of Annotations and Interfaces
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      return emptyResult;
    }
    // skip processing if disabled, or no lombok library is present
    if (!LombokLibraryUtil.hasLombokLibrary(element.getProject())) {
      return emptyResult;
    }

    // All invoker of AugmentProvider already make caching
    // and we want to try to skip recursive calls completely

///      final String message = String.format("Process call for type: %s class: %s", type.getSimpleName(), psiClass.getQualifiedName());
//      log.info(">>>" + message);
    final List<Psi> result = getPsis(psiClass, type, nameHint);
//      log.info("<<<" + message);
    return result;
  }

  @NotNull
  private static <Psi extends PsiElement> List<Psi> getPsis(PsiClass psiClass, Class<Psi> type, String nameHint) {
    final List<Psi> result = new ArrayList<>();
    for (Processor processor : LombokProcessorManager.getProcessors(type)) {
      final List<? super PsiElement> generatedElements = processor.process(psiClass, nameHint);
      for (Object psiElement : generatedElements) {
        result.add((Psi) psiElement);
      }
    }
    return result;
  }

  @Override
  protected List<PsiExtensionMethod> getExtensionMethods(@NotNull PsiClass aClass,
                                                         @NotNull String nameHint,
                                                         @NotNull PsiElement context) {
    if (!LombokLibraryUtil.hasLombokLibrary(context.getProject())) {
      return Collections.emptyList();
    }
    return ExtensionMethodsHelper.getExtensionMethods(aClass, nameHint, context);
  }
}
