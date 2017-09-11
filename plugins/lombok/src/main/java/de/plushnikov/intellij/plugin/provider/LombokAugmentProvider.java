package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.agent.transformer.ModifierVisibilityClassFileTransformer;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.processor.modifier.ModifierProcessor;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger log = Logger.getInstance(LombokAugmentProvider.class.getName());

  private final ValProcessor valProcessor = new ValProcessor();
  private final Collection<ModifierProcessor> modifierProcessors;

  public LombokAugmentProvider() {
    log.debug("LombokAugmentProvider created");

    modifierProcessors = Arrays.asList(getModifierProcessors());
  }

  /**
   * Support method required by patcher project and {@link ModifierVisibilityClassFileTransformer}.
   * Provides a simple way to inject modifiers into older versions of IntelliJ. Return of the null value is dictated by legacy IntelliJ API.
   *
   * @param modifierList PsiModifierList that is being queried
   * @param name         String name of the PsiModifier
   * @return {@code Boolean.TRUE} if modifier exists (explicitly set by modifier transformers of the plugin), {@code null} otherwise.
   */
  public Boolean hasModifierProperty(@NotNull PsiModifierList modifierList, @NotNull final String name) {
    if (DumbService.isDumb(modifierList.getProject())) {
      return null;
    }

    final Set<String> modifiers = this.transformModifiers(modifierList, Collections.<String>emptySet());
    if (modifiers.contains(name)) {
      return Boolean.TRUE;
    }

    return null;
  }

  @NotNull
  @Override
  protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
    // make copy of original modifiers
    Set<String> result = ContainerUtil.newHashSet(modifiers);

    // Loop through all available processors and give all of them a chance to respond
    for (ModifierProcessor processor : modifierProcessors) {
      if (processor.isSupported(modifierList)) {
        processor.transformModifiers(modifierList, result);
      }
    }

    return result;
  }

  @Nullable
  @Override
  protected PsiType inferType(@NotNull PsiTypeElement typeElement) {
    if (DumbService.isDumb(typeElement.getProject()) || !valProcessor.isEnabled(typeElement.getProject())) {
      return null;
    }
    return valProcessor.inferType(typeElement);
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull final Class<Psi> type) {
    final List<Psi> emptyResult = Collections.emptyList();
    // skip processing during index rebuild
    final Project project = element.getProject();
    if (DumbService.isDumb(project)) {
      return emptyResult;
    }
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code auto completion will not work
    if (!(element instanceof PsiExtensibleClass) || !element.isValid()) {
      return emptyResult;
    }
    // Skip processing of Annotations and Interfaces
    if (((PsiClass) element).isAnnotationType() || ((PsiClass) element).isInterface()) {
      return emptyResult;
    }

    // skip processing if plugin is disabled
    if (!ProjectSettings.isLombokEnabledInProject(project)) {
      return emptyResult;
    }

    final PsiClass psiClass = (PsiClass) element;

    if (type == PsiField.class) {
      return CachedValuesManager.getCachedValue(element, new FieldLombokCachedValueProvider<Psi>(type, psiClass));
    } else if (type == PsiMethod.class) {
      return CachedValuesManager.getCachedValue(element, new MethodLombokCachedValueProvider<Psi>(type, psiClass));
    } else if (type == PsiClass.class) {
      return CachedValuesManager.getCachedValue(element, new ClassLombokCachedValueProvider<Psi>(type, psiClass));
    } else {
      return emptyResult;
    }
  }

  private ModifierProcessor[] getModifierProcessors() {
    return LombokProcessorExtensionPoint.EP_NAME_MODIFIER_PROCESSOR.getExtensions();
  }

  private static class FieldLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    FieldLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass);
    }
  }

  private static class MethodLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    MethodLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass);
    }
  }

  private static class ClassLombokCachedValueProvider<Psi extends PsiElement> extends LombokCachedValueProvider<Psi> {
    ClassLombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      super(type, psiClass);
    }
  }

  private static class LombokCachedValueProvider<Psi extends PsiElement> implements CachedValueProvider<List<Psi>> {
    private final Class<Psi> type;
    private final PsiClass psiClass;

    LombokCachedValueProvider(Class<Psi> type, PsiClass psiClass) {
      this.type = type;
      this.psiClass = psiClass;
    }

    @Nullable
    @Override
    public Result<List<Psi>> compute() {
      if (log.isDebugEnabled()) {
        log.debug(String.format("Process call for type: %s class: %s", type, psiClass.getQualifiedName()));
      }

      final List<Psi> result = new ArrayList<Psi>();
      final Collection<Processor> lombokProcessors = LombokProcessorProvider.getInstance(psiClass.getProject()).getLombokProcessors(type);
      for (Processor processor : lombokProcessors) {
        result.addAll((Collection<Psi>) processor.process(psiClass));
      }
      return new Result<List<Psi>>(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    }
  }
}
