package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiClassImpl;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.processor.clazz.LombokClassProcessor;
import de.plushnikov.intellij.lombok.processor.field.LombokFieldProcessor;
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

  /**
   * To add a getters based on field annotations the provider should be implemented somewhat like this:
   * only respond to requests where parameter "element" is of type PsiClass and "type" equals to PsiMethod.class;
   * find all fields in a given  "element" for which a getter should be added;
   * and return a PsiMethod implementation for each such field
   * (for Lombok I think LightMethod instances should be used, see PsiClassImpl code for example)
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

    final PsiMethod[] classMethods = collectClassMethodsIntern((PsiClassImpl) psiClass);
    if (type.isAssignableFrom(PsiField.class)) {
      LOG.info("collect field of class: " + psiClass.getQualifiedName());
      processPsiClassAnnotations(result, psiClass, classMethods, type);

    } else if (type.isAssignableFrom(PsiMethod.class)) {
      LOG.info("collect methods of class: " + psiClass.getQualifiedName());

      cleanAttributeUsage(psiClass);
      processPsiClassAnnotations(result, psiClass, classMethods, type);
      processPsiClassFieldAnnotation(result, psiClass, classMethods, type);
    }
    return result;
  }

  /**
   * Workaround to get all of original Methods of the psiClass.
   * Normal call to psiClass.getMethods() is impossible because of incorrect cache implementation of IntelliJ Idea
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern methods of the class
   * @deprecated
   */
  @Deprecated
  private PsiMethod[] collectClassMethodsIntern(@NotNull PsiClassImpl psiClass) {
    return psiClass.getStubOrPsiChildren(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY);
  }

  protected void cleanAttributeUsage(PsiClass psiClass) {
    for (PsiField psiField : psiClass.getFields()) {
      UserMapKeys.removeAllUsagesFrom(psiField);
    }
  }

  private <Psi extends PsiElement> void processPsiClassAnnotations(@NotNull List<Psi> result, @NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull Class<Psi> type) {
    LOG.info("Processing class annotations BEGINN: " + psiClass.getQualifiedName());

    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processClassAnnotation(psiAnnotation, psiClass, classMethods, result, type);
      }
    }
    LOG.info("Processing class annotations END: " + psiClass.getQualifiedName());
  }

  private <Psi extends PsiElement> void processClassAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull List<Psi> result, @NotNull Class<Psi> type) {
    for (LombokClassProcessor classProcessor : allClassHandlers) {
      if (classProcessor.acceptAnnotation(psiAnnotation, type)) {
        classProcessor.process(psiClass, classMethods, psiAnnotation, result);
      }
    }
  }

  protected <Psi extends PsiElement> void processPsiClassFieldAnnotation(@NotNull List<Psi> result, @NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull Class<Psi> type) {
    LOG.info("Processing field annotations BEGINN: " + psiClass.getQualifiedName());

    for (PsiField psiField : psiClass.getFields()) {
      processField(result, psiField, classMethods, type);
    }
    LOG.info("Processing field annotations END: " + psiClass.getQualifiedName());
  }

  protected <Psi extends PsiElement> void processField(@NotNull List<Psi> result, @NotNull PsiField psiField, @NotNull PsiMethod[] classMethods, @NotNull Class<Psi> type) {
    final PsiModifierList modifierList = psiField.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        processFieldAnnotation(psiAnnotation, psiField, classMethods, result, type);
      }
    }
  }

  private <Psi extends PsiElement> void processFieldAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull PsiMethod[] classMethods, @NotNull List<Psi> result, @NotNull Class<Psi> type) {
    for (LombokFieldProcessor fieldProcessor : allFieldHandlers) {
      if (fieldProcessor.acceptAnnotation(psiAnnotation, type)) {
        fieldProcessor.process(psiField, classMethods, psiAnnotation, result);
      }
    }
  }

}
