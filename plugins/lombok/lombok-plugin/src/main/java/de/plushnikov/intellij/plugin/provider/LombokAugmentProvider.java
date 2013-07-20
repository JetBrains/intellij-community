package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.augment.PsiAugmentProvider;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.processor.clazz.LombokClassProcessor;
import de.plushnikov.intellij.lombok.processor.field.LombokFieldProcessor;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.core.GenericServiceLocator;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger LOG = Logger.getInstance(LombokAugmentProvider.class.getName());

  private final Collection<LombokFieldProcessor> allFieldHandlers;
  private final Collection<LombokClassProcessor> allClassHandlers;

  public LombokAugmentProvider() {
    List<LombokClassProcessor> lombokClassProcessors = GenericServiceLocator.locateAll(LombokClassProcessor.class);
    List<LombokFieldProcessor> lombokFieldProcessors = GenericServiceLocator.locateAll(LombokFieldProcessor.class);

    allClassHandlers = new HashSet<LombokClassProcessor>(lombokClassProcessors);
    allFieldHandlers = new HashSet<LombokFieldProcessor>(lombokFieldProcessors);
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    List<Psi> emptyResult = Collections.emptyList();
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code auto completion will not work
    if (!(element instanceof PsiClass) || !element.isValid()) {
      return emptyResult;
    }
    // skip processing during index rebuild
    final Project project = element.getProject();
    if (DumbService.getInstance(project).isDumb()) {
      return emptyResult;
    }
    // skip processing if plugin is disabled
    if (!ProjectSettings.loadAndGetEnabledInProject(project)) {
      return emptyResult;
    }

    List<PsiElement> result = new ArrayList<PsiElement>();
    final PsiClass psiClass = (PsiClass) element;
    if (type.isAssignableFrom(PsiField.class)) {
      LOG.debug("collect field of class: " + psiClass.getQualifiedName());

      processPsiClassAnnotations(result, psiClass, type);
    } else if (type.isAssignableFrom(PsiMethod.class)) {
      LOG.debug("collect methods of class: " + psiClass.getQualifiedName());

      cleanAttributeUsage(psiClass);
      processPsiClassAnnotations(result, psiClass, type);
      processPsiClassFieldAnnotation(result, psiClass, type);
    } else if (type.isAssignableFrom(PsiClass.class)) {
      LOG.debug("collect inner classes of class: " + psiClass.getQualifiedName());

    }
    return (List<Psi>) result;
  }

  protected void cleanAttributeUsage(PsiClass psiClass) {
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      UserMapKeys.removeAllUsagesFrom(psiField);
    }
  }

  private void processPsiClassAnnotations(@NotNull List<? super PsiElement> result, @NotNull PsiClass psiClass, @NotNull Class<? extends PsiElement> type) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processClassAnnotation(psiAnnotation, psiClass, result, type);
      }
    }
  }

  private void processClassAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull List<? super PsiElement> result, @NotNull Class<? extends PsiElement> type) {
    for (LombokClassProcessor classProcessor : allClassHandlers) {
      if (classProcessor.acceptAnnotation(psiAnnotation, type)) {
        classProcessor.process(psiClass, psiAnnotation, result);
      }
    }
  }

  protected void processPsiClassFieldAnnotation(@NotNull List<? super PsiElement> result, @NotNull PsiClass psiClass, @NotNull Class<? extends PsiElement> type) {
    for (PsiField psiField : psiClass.getFields()) {
      processField(result, psiField, type);
    }
  }

  protected void processField(@NotNull List<? super PsiElement> result, @NotNull PsiField psiField, @NotNull Class<? extends PsiElement> type) {
    final PsiModifierList modifierList = psiField.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processFieldAnnotation(psiAnnotation, psiField, result, type);
      }
    }
  }

  private void processFieldAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull List<? super PsiElement> result, @NotNull Class<? extends PsiElement> type) {
    for (LombokFieldProcessor fieldProcessor : allFieldHandlers) {
      if (fieldProcessor.acceptAnnotation(psiAnnotation, type)) {
        fieldProcessor.process(psiField, psiAnnotation, result);
      }
    }
  }

}
