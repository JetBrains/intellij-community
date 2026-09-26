package de.plushnikov.intellij.plugin.provider;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.siyeh.ig.psiutils.InitializationUtils;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.processor.lombok.LombokAnnotationProcessor;
import de.plushnikov.intellij.plugin.processor.method.ExtensionMethodsHelper;
import de.plushnikov.intellij.plugin.processor.modifier.ModifierProcessor;
import de.plushnikov.intellij.plugin.util.DumbIncompleteModeUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.plushnikov.intellij.plugin.util.LombokLibraryUtil.hasLombokLibrary;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public final class LombokAugmentProvider extends PsiAugmentProvider implements PossiblyDumbAware {
  private static final class Holder {
    static final Collection<ModifierProcessor> modifierProcessors = LombokProcessorManager.getLombokModifierProcessors();
  }

  public LombokAugmentProvider() {
  }

  @Override
  public boolean isDumbAware() {
    return Registry.is("lombok.dumb.mode.enabled", false);
  }

  @Override
  protected @NotNull Set<String> transformModifiers(@NotNull PsiModifierList modifierList, final @NotNull Set<String> modifiers) {
    // skip if no lombok library is present
    if (!hasLombokLibrary(modifierList.getProject()) && !DumbIncompleteModeUtil.isIncompleteModeWithLombokAnnotation(modifierList)) {
      return modifiers;
    }

    // make copy of original modifiers
    Set<String> result = new HashSet<>(modifiers);

    DumbService dumbService = DumbService.getInstance(modifierList.getProject());
    Runnable runnable = () -> {
      // Loop through all available processors and give all of them a chance to respond
      for (ModifierProcessor processor : Holder.modifierProcessors) {
        if (processor.isSupported(modifierList)) {
          processor.transformModifiers(modifierList, result);
        }
      }
    };
    if (dumbService.isDumb() && !dumbService.isAlternativeResolveEnabled()) {
      dumbService.runWithAlternativeResolveEnabled(() -> runnable.run());
    }
    else {
      runnable.run();
    }
    return result;
  }

  @Override
  public boolean canInferType(@NotNull PsiTypeElement typeElement) {
    //skip if dumb mode, allow only `getAugments`
    if (DumbService.isDumb(typeElement.getProject())) return false;

    return hasLombokLibrary(typeElement.getProject()) && ValProcessor.canInferType(typeElement);
  }

  /*
   * The final fields that are marked with Builder.Default contains only possible value
   * because user can set another value during the creation of the object.
   *
   * The fields marked with Getter(lazy=true) contains a value that will be calculated only at first access to the getter-Method
   */
  //see de.plushnikov.intellij.plugin.inspection.DataFlowInspectionTest.testDefaultBuilderFinalValueInspectionIsAlwaysThat
  //see de.plushnikov.intellij.plugin.inspection.PointlessBooleanExpressionInspectionTest.testPointlessBooleanExpressionBuilderDefault
  //see com.intellij.java.lomboktest.LombokHighlightingTest.testBuilderWithDefaultRedundantInitializer
  //see com.intellij.java.lomboktest.LombokHighlightingTest.testGetterLazyInvocationProduceNPE
  //see com.intellij.java.lomboktest.LombokHighlightingTest.testGetterLazyVariableNotInitialized
  @Override
  protected boolean fieldInitializerMightBeChanged(@NotNull PsiField field) {
    //skip if dumb mode, allow only `getAugments`
    if (DumbService.isDumb(field.getProject())) return false;

    if (PsiAnnotationSearchUtil.isAnnotatedWith(field, LombokClassNames.BUILDER_DEFAULT)) {
      return true;
    }

    final PsiAnnotation getterAnnotation = PsiAnnotationSearchUtil.findAnnotation(field, LombokClassNames.GETTER);
    final boolean isLazyGetter = null != getterAnnotation &&
                                 PsiAnnotationUtil.getBooleanAnnotationValue(getterAnnotation, "lazy", false);

    if (isLazyGetter) {
      final PsiExpression fieldInitializer = field.getInitializer();
      if (fieldInitializer instanceof PsiMethodCallExpression methodCallExpression) {
        final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
        if (qualifierExpression instanceof PsiReferenceExpression qualifierReferenceExpression) {
          final PsiElement referencedElement = qualifierReferenceExpression.resolve();
          if (referencedElement instanceof PsiField referencedField) {
            final PsiClass containingClass = referencedField.getContainingClass();
            if (containingClass != null) {
              return InitializationUtils.isInitializedInConstructors(referencedField, containingClass);
            }
          }
        }
      }
    }
    return isLazyGetter;
  }

  @Override
  protected @Nullable PsiType inferType(@NotNull PsiTypeElement typeElement) {
    //skip if dumb mode, allow only `getAugments`
    if (DumbService.isDumb(typeElement.getProject())) return null;

    // Do not check whether lombok library is available.
    // While this check is cached, first time it could be quite heavy, so let's postpone it.
    // It's actually cheaper to avoid it here, as most of the types aren't shaped as 'var' and 'val',
    // so we easily exclude them.
    // Without Lombok library, lombok.var/lombok.val will not be resolvable anyway.
    return ValProcessor.inferType(typeElement);
  }

  @Override
  public @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                 final @NotNull Class<Psi> type) {
    return getAugments(element, type, null);
  }

  @Override
  public @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                 final @NotNull Class<Psi> type,
                                                                 @Nullable String nameHint) {
    final List<Psi> emptyResult = Collections.emptyList();
    if (type != PsiClass.class && type != PsiField.class && type != PsiMethod.class || !(element instanceof PsiExtensibleClass psiClass)) {
      return emptyResult;
    }

    if (!psiClass.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      return emptyResult;
    }

    // skip processing if disabled, or no lombok library is present
    if (!hasLombokLibrary(element.getProject()) && !DumbIncompleteModeUtil.isIncompleteModeWithLombokAnnotation(psiClass)) {
      return emptyResult;
    }
    if (psiClass.isAnnotationType() && type == PsiMethod.class) {
      @SuppressWarnings("unchecked")
      final List<Psi> result = (List<Psi>)LombokAnnotationProcessor.process(psiClass, nameHint);
      return result;
    }
    // Skip processing of other Annotations and Interfaces
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      return emptyResult;
    }
    if (element instanceof PsiCompiledElement) { // skip compiled classes)
      return emptyResult;
    }

    // All invokers of AugmentProvider already make caching,
    // and we want to try to skip recursive calls completely
    DumbService dumbService = DumbService.getInstance(psiClass.getProject());
    if (DumbService.isDumb(psiClass.getProject()) && !dumbService.isAlternativeResolveEnabled()) {
      return dumbService.computeWithAlternativeResolveEnabled(()-> getPsis(psiClass, type, nameHint));
    }
    return getPsis(psiClass, type, nameHint);
  }

  private static @NotNull <Psi extends PsiElement> List<Psi> getPsis(PsiClass psiClass, Class<Psi> type, String nameHint) {
    final List<Psi> result = new ArrayList<>();
    for (Processor processor : LombokProcessorManager.getProcessors(type)) {
      final List<? super PsiElement> generatedElements = processor.process(psiClass, nameHint);
      for (Object psiElement : generatedElements) {
        @SuppressWarnings("unchecked")
        final Psi element = (Psi)psiElement;
        result.add(element);
      }
    }
    return result;
  }

  @Override
  protected List<PsiExtensionMethod> getExtensionMethods(@NotNull PsiClass aClass,
                                                         @NotNull String nameHint,
                                                         @NotNull PsiElement context) {
    //skip if dumb mode, allow only `getAugments`
    if (DumbService.isDumb(aClass.getProject())) return Collections.emptyList();

    if (!hasLombokLibrary(context.getProject())) {
      return Collections.emptyList();
    }
    return ExtensionMethodsHelper.getExtensionMethods(aClass, nameHint, context);
  }
}
