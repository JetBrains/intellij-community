package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.processor.clazz.LombokClassProcessor;
import de.plushnikov.intellij.lombok.processor.field.LombokFieldProcessor;
import de.plushnikov.intellij.plugin.core.GenericServiceLocator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

  /**
   * To add a getters based on field annotations the provider should be implemented somewhat like this:
   * only respond to requests where parameter "element" is of type PsiClass and "type" equals to PsiMethod.class;
   * find all fields in a given  "element" for which a getter should be added;
   * and return a PsiMethod implementation for each such field
   * (for Lombok I think LightMethod instances should be used, see PsiClassImpl code for example)
   *
   * @param element
   * @param type
   * @param <Psi>
   * @return
   */
  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    // Expecting that we are only augmenting an PsiClass
    if (!(element instanceof PsiClass) || !element.isValid() || !element.isPhysical()) {
      //noinspection unchecked
      return Collections.emptyList();
    }
    final List<Psi> result = new ArrayList<Psi>();
    final PsiClass psiClass = (PsiClass) element;
    LOG.info("Called for class: " + psiClass.getQualifiedName() + " type: " + type.getName());

    if (type.isAssignableFrom(PsiField.class)) {
      LOG.info("collect field of class: " + psiClass.getQualifiedName());
      processPsiClassAnnotations(result, psiClass, type);
    } else if (type.isAssignableFrom(PsiMethod.class)) {
      LOG.info("collect methods of class: " + psiClass.getQualifiedName());
      processPsiClassAnnotations(result, psiClass, type);
      processPsiClassFieldAnnotation(result, psiClass, type);
    }
    return result;
  }

  private <Psi extends PsiElement> void processPsiClassAnnotations(@NotNull List<Psi> result, @NotNull PsiClass psiClass, @NotNull Class<Psi> type) {
    LOG.info("Processing class annotations BEGINN: " + psiClass.getQualifiedName());

    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processClassAnnotation(psiAnnotation, psiClass, result, type);
      }
    }
    LOG.info("Processing class annotations END: " + psiClass.getQualifiedName());
  }

  private <Psi extends PsiElement> boolean processClassAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull List<Psi> result, @NotNull Class<Psi> type) {
    boolean processed = false;
    for (LombokClassProcessor classProcessor : allClassHandlers) {
      if (classProcessor.acceptAnnotation(psiAnnotation.getQualifiedName(), type)) {
        processed |= classProcessor.process(psiClass, psiAnnotation, result);
      }
    }
    return processed;
  }

  protected <Psi extends PsiElement> void processPsiClassFieldAnnotation(@NotNull List<Psi> result, @NotNull PsiClass psiClass, @NotNull Class<Psi> type) {
    LOG.info("Processing field annotations BEGINN: " + psiClass.getQualifiedName());

    for (PsiField psiField : psiClass.getFields()) {
      processField(result, psiField, type);
    }
    LOG.info("Processing field annotations END: " + psiClass.getQualifiedName());
  }

  protected <Psi extends PsiElement> void processField(@NotNull List<Psi> result, @NotNull PsiField psiField, @NotNull Class<Psi> type) {
    final PsiModifierList modifierList = psiField.getModifierList();
    if (modifierList != null) {
      boolean processed = false;

      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processed |= processFieldAnnotation(psiAnnotation, psiField, result, type);
      }

      psiField.putUserData(UserMapKeys.USAGE_KEY, processed);
      if (!processed) {
        psiField.putUserData(UserMapKeys.READ_KEY, false);
        psiField.putUserData(UserMapKeys.WRITE_KEY, false);
      }
    }
  }

  private <Psi extends PsiElement> boolean processFieldAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull List<Psi> result, @NotNull Class<Psi> type) {
    boolean processed = false;
    for (LombokFieldProcessor fieldProcessor : allFieldHandlers) {
      if (fieldProcessor.acceptAnnotation(psiAnnotation.getQualifiedName(), type)) {
        processed |= fieldProcessor.process(psiField, psiAnnotation, result);
      }
    }
    return processed;
  }

}
