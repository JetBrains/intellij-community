package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import de.plushnikov.intellij.plugin.activity.LombokProjectValidatorActivity;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.processor.method.ExtensionMethodsHelper;
import de.plushnikov.intellij.plugin.processor.modifier.ModifierProcessor;
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
    valProcessor = ApplicationManager.getApplication().getService(ValProcessor.class);
  }

  @NotNull
  @Override
  protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
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

  /**
   * This method should be available in the next IntelliJ 203 Release
   */
  @Override
  public boolean canInferType(@NotNull PsiTypeElement typeElement) {
    return valProcessor.canInferType(typeElement);
  }

  @Nullable
  @Override
  protected PsiType inferType(@NotNull PsiTypeElement typeElement) {
    return valProcessor.inferType(typeElement);
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull final Class<Psi> type) {
    final List<Psi> emptyResult = Collections.emptyList();
    if ((type != PsiClass.class && type != PsiField.class && type != PsiMethod.class) || !(element instanceof PsiExtensibleClass)) {
      return emptyResult;
    }

    final PsiClass psiClass = (PsiClass) element;
    // Skip processing of Annotations and Interfaces
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      return emptyResult;
    }
    // skip processing if disabled, or no lombok library is present
    if (!LombokProjectValidatorActivity.hasLombokLibrary(element.getProject())) {
      return emptyResult;
    }

    final List<Psi> cachedValue;
    if (type == PsiField.class) {
      cachedValue = CachedValuesManager.getCachedValue(element, new FieldLombokCachedValueProvider<>(type, psiClass));
    } else if (type == PsiMethod.class) {
      cachedValue = CachedValuesManager.getCachedValue(element, new MethodLombokCachedValueProvider<>(type, psiClass));
    } else {
      cachedValue = CachedValuesManager.getCachedValue(element, new ClassLombokCachedValueProvider<>(type, psiClass));
    }
    return null != cachedValue ? cachedValue : emptyResult;
  }

  private static class FieldLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    private static final RecursionGuard<PsiClass> ourGuard = RecursionManager.createGuard("lombok.augment.field");

    FieldLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass, ourGuard);
    }
  }

  private static class MethodLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    private static final RecursionGuard<PsiClass> ourGuard = RecursionManager.createGuard("lombok.augment.method");

    MethodLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass, ourGuard);
    }
  }

  private static class ClassLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    private static final RecursionGuard<PsiClass> ourGuard = RecursionManager.createGuard("lombok.augment.class");

    ClassLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass, ourGuard);
    }
  }

  private abstract static class LombokCachedValueProvider<Psi extends PsiElement> implements CachedValueProvider<List<Psi>> {
    private final Class<Psi> type;
    private final PsiClass psiClass;
    private final RecursionGuard<PsiClass> recursionGuard;

    LombokCachedValueProvider(Class<Psi> type, PsiClass psiClass, RecursionGuard<PsiClass> recursionGuard) {
      this.type = type;
      this.psiClass = psiClass;
      this.recursionGuard = recursionGuard;
    }

    @Nullable
    @Override
    public Result<List<Psi>> compute() {
//      return computeIntern();
      return recursionGuard.doPreventingRecursion(psiClass, true, this::computeIntern);
    }

    private Result<List<Psi>> computeIntern() {
//      final String message = String.format("Process call for type: %s class: %s", type.getSimpleName(), psiClass.getQualifiedName());
//      log.info(">>>" + message);
      final List<Psi> result = getPsis(psiClass, type);
//      log.info("<<<" + message);
      return Result.create(result, psiClass);
    }
  }

  @NotNull
  private static <Psi extends PsiElement> List<Psi> getPsis(PsiClass psiClass, Class<Psi> type) {
    final List<Psi> result = new ArrayList<>();
    final Collection<Processor> lombokProcessors = LombokProcessorProvider.getInstance(psiClass.getProject()).getLombokProcessors(type);
    for (Processor processor : lombokProcessors) {
      final List<? super PsiElement> generatedElements = processor.process(psiClass);
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
    if (!LombokProjectValidatorActivity.hasLombokLibrary(context.getProject())) {
      return Collections.emptyList();
    }
    return ExtensionMethodsHelper.getExtensionMethods(aClass, nameHint, context);
  }
}
