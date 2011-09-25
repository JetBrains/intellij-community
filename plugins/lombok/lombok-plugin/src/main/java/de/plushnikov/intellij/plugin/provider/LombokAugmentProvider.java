package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
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
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code autocompletion will not work
    if (!(element instanceof PsiClass) || !element.isValid()) {
      //noinspection unchecked
      return Collections.emptyList();
    }
    final List<Psi> result = new ArrayList<Psi>();
    final PsiClass psiClass = (PsiClass) element;

    if (type.isAssignableFrom(PsiField.class)) {
      LOG.info("collect field of class: " + psiClass.getQualifiedName());
      processPsiClassAnnotations(result, psiClass, type);

    } else if (type.isAssignableFrom(PsiMethod.class)) {
      LOG.info("collect methods of class: " + psiClass.getQualifiedName());

      cleanAttributeUsage(psiClass);
      processPsiClassAnnotations(result, psiClass, type);
      processPsiClassFieldAnnotation(result, psiClass, type);
    }
    return result;
  }

  protected void cleanAttributeUsage(PsiClass psiClass) {
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      UserMapKeys.removeAllUsagesFrom(psiField);
    }
  }

  private <Psi extends PsiElement> void processPsiClassAnnotations(@NotNull List<Psi> result, @NotNull PsiClass psiClass, @NotNull Class<Psi> type) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processClassAnnotation(psiAnnotation, psiClass, result, type);
      }
    }
  }

  private <Psi extends PsiElement> void processClassAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull List<Psi> result, @NotNull Class<Psi> type) {
    for (LombokClassProcessor classProcessor : allClassHandlers) {
      if (classProcessor.acceptAnnotation(psiAnnotation, type)) {
        classProcessor.process(psiClass, psiAnnotation, result);
      }
    }
  }

  protected <Psi extends PsiElement> void processPsiClassFieldAnnotation(@NotNull List<Psi> result, @NotNull PsiClass psiClass, @NotNull Class<Psi> type) {
    for (PsiField psiField : psiClass.getFields()) {
      processField(result, psiField, type);
    }
  }

  protected <Psi extends PsiElement> void processField(@NotNull List<Psi> result, @NotNull PsiField psiField, @NotNull Class<Psi> type) {
    final PsiModifierList modifierList = psiField.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processFieldAnnotation(psiAnnotation, psiField, result, type);
      }
    }
  }

  private <Psi extends PsiElement> void processFieldAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull List<Psi> result, @NotNull Class<Psi> type) {
    for (LombokFieldProcessor fieldProcessor : allFieldHandlers) {
      if (fieldProcessor.acceptAnnotation(psiAnnotation, type)) {
        fieldProcessor.process(psiField, psiAnnotation, result);
      }
    }
  }

}
