package de.plushnikov.intellij.plugin.provider;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LombokProcessorProvider {

  public static LombokProcessorProvider getInstance(@NotNull Project project) {
    final LombokProcessorProvider service = ServiceManager.getService(project, LombokProcessorProvider.class);
    service.checkInitialized();
    return service;
  }

  private final PropertiesComponent myPropertiesComponent;

  private final Map<Class, Collection<Processor>> lombokTypeProcessors;
  private final Map<String, Collection<Processor>> lombokProcessors;
  private final Collection<String> registeredAnnotationNames;

  private boolean alreadyInitialized;

  public LombokProcessorProvider(@NotNull PropertiesComponent propertiesComponent) {
    myPropertiesComponent = propertiesComponent;

    lombokProcessors = new ConcurrentHashMap<>();
    lombokTypeProcessors = new ConcurrentHashMap<>();
    registeredAnnotationNames = ConcurrentHashMap.newKeySet();
  }

  private void checkInitialized() {
    if (!alreadyInitialized) {
      initProcessors();
      alreadyInitialized = true;
    }
  }

  public void initProcessors() {
    lombokProcessors.clear();
    lombokTypeProcessors.clear();
    registeredAnnotationNames.clear();

    for (Processor processor : LombokProcessorManager.getLombokProcessors()) {
      if (processor.isEnabled(myPropertiesComponent)) {

        Class<? extends Annotation>[] annotationClasses = processor.getSupportedAnnotationClasses();
        for (Class<? extends Annotation> annotationClass : annotationClasses) {
          putProcessor(lombokProcessors, annotationClass.getName(), processor);
          putProcessor(lombokProcessors, annotationClass.getSimpleName(), processor);
        }

        putProcessor(lombokTypeProcessors, processor.getSupportedClass(), processor);
      }
    }

    registeredAnnotationNames.addAll(lombokProcessors.keySet());
  }

  @NotNull
  Collection<Processor> getLombokProcessors(@NotNull Class supportedClass) {
    return lombokTypeProcessors.computeIfAbsent(supportedClass, k -> ConcurrentHashMap.newKeySet());
  }

  @NotNull
  public Collection<Processor> getProcessors(@NotNull PsiAnnotation psiAnnotation) {
    final String qualifiedName = psiAnnotation.getQualifiedName();
    final Collection<Processor> result = qualifiedName == null ? null : lombokProcessors.get(qualifiedName);
    return result == null ? Collections.emptySet() : result;
  }

  @NotNull
  Collection<LombokProcessorData> getApplicableProcessors(@NotNull PsiMember psiMember) {
    Collection<LombokProcessorData> result = Collections.emptyList();
    if (verifyLombokAnnotationPresent(psiMember)) {
      result = new ArrayList<>();

      addApplicableProcessors(psiMember, result);
      final PsiClass psiClass = psiMember.getContainingClass();
      if (null != psiClass) {
        addApplicableProcessors(psiClass, result);
      }
    }
    return result;
  }

  private <K, V> void putProcessor(final Map<K, Collection<V>> map, final K key, final V value) {
    Collection<V> valueList = map.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
    valueList.add(value);
  }

  private boolean verifyLombokAnnotationPresent(@NotNull PsiClass psiClass) {
    if (PsiAnnotationSearchUtil.checkAnnotationsSimpleNameExistsIn(psiClass, registeredAnnotationNames)) {
      return true;
    }
    Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      if (PsiAnnotationSearchUtil.checkAnnotationsSimpleNameExistsIn(psiField, registeredAnnotationNames)) {
        return true;
      }
    }
    Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    for (PsiMethod psiMethod : psiMethods) {
      if (PsiAnnotationSearchUtil.checkAnnotationsSimpleNameExistsIn(psiMethod, registeredAnnotationNames)) {
        return true;
      }
    }
    final PsiElement psiClassParent = psiClass.getParent();
    if (psiClassParent instanceof PsiClass) {
      return verifyLombokAnnotationPresent((PsiClass) psiClassParent);
    }

    return false;
  }

  private boolean verifyLombokAnnotationPresent(@NotNull PsiMember psiMember) {
    if (PsiAnnotationSearchUtil.checkAnnotationsSimpleNameExistsIn(psiMember, registeredAnnotationNames)) {
      return true;
    }

    final PsiClass psiClass = psiMember.getContainingClass();
    return null != psiClass && verifyLombokAnnotationPresent(psiClass);
  }

  private void addApplicableProcessors(@NotNull PsiMember psiMember, @NotNull Collection<LombokProcessorData> target) {
    final PsiModifierList psiModifierList = psiMember.getModifierList();
    if (null != psiModifierList) {
      for (PsiAnnotation psiAnnotation : psiModifierList.getAnnotations()) {
        for (Processor processor : getProcessors(psiAnnotation)) {
          target.add(new LombokProcessorData(processor, psiAnnotation));
        }
      }
    }
  }
}
