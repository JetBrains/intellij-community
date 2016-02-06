package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class LombokProcessorProvider {
  private static LombokProcessorProvider ourInstance = new LombokProcessorProvider();

  public static LombokProcessorProvider getInstance() {
    return ourInstance;
  }

  private final Map<Class, Collection<Processor>> lombokTypeProcessors;
  private final Map<String, Collection<Processor>> lombokProcessors;
  private final Collection<String> registeredAnnotationNames;

  private LombokProcessorProvider() {
    lombokProcessors = new HashMap<String, Collection<Processor>>();
    lombokTypeProcessors = new HashMap<Class, Collection<Processor>>();
    registeredAnnotationNames = new HashSet<String>();

    initProcessors(null);
  }

  public void initProcessors(@Nullable Project project) {
    lombokProcessors.clear();
    lombokTypeProcessors.clear();
    registeredAnnotationNames.clear();

    for (Processor processor : getLombokProcessors()) {
      if (null == project || processor.isEnabled(project)) {

        Class<? extends Annotation> annotationClass = processor.getSupportedAnnotationClass();

        putProcessor(lombokProcessors, annotationClass.getName(), processor);
        putProcessor(lombokProcessors, annotationClass.getSimpleName(), processor);

        putProcessor(lombokTypeProcessors, processor.getSupportedClass(), processor);
      }
    }

    registeredAnnotationNames.addAll(lombokProcessors.keySet());
  }

  @NotNull
  private Processor[] getLombokProcessors() {
    return LombokProcessorExtensionPoint.EP_NAME.getExtensions();
  }

  private <K, V> void putProcessor(final Map<K, Collection<V>> map, final K key, final V value) {
    Collection<V> valueList = map.get(key);
    if (null == valueList) {
      valueList = new ArrayList<V>();
      map.put(key, valueList);
    }
    valueList.add(value);
  }

  @NotNull
  public Collection<Processor> getLombokProcessors(@NotNull Class supportedClass) {
    final Collection<Processor> result = lombokTypeProcessors.get(supportedClass);
    return result == null ? Collections.<Processor>emptySet() : result;
  }

  @NotNull
  public Collection<Processor> getProcessors(@NotNull PsiAnnotation psiAnnotation) {
    final String qualifiedName = psiAnnotation.getQualifiedName();
    final Collection<Processor> result = qualifiedName == null ? null : lombokProcessors.get(qualifiedName);
    return result == null ? Collections.<Processor>emptySet() : result;
  }

  public boolean verifyLombokAnnotationPresent(@NotNull PsiClass psiClass) {
    if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiClass, registeredAnnotationNames)) {
      return true;
    }
    Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiField, registeredAnnotationNames)) {
        return true;
      }
    }
    Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    for (PsiMethod psiMethod : psiMethods) {
      if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiMethod, registeredAnnotationNames)) {
        return true;
      }
    }
    final PsiElement psiClassParent = psiClass.getParent();
    if (psiClassParent instanceof PsiClass) {
      return verifyLombokAnnotationPresent((PsiClass) psiClassParent);
    }

    return false;
  }

  public boolean verifyLombokAnnotationPresent(@NotNull PsiMember psiMember) {
    if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiMember, registeredAnnotationNames)) {
      return true;
    }

    final PsiClass psiClass = psiMember.getContainingClass();
    return null != psiClass && verifyLombokAnnotationPresent(psiClass);
  }

  @NotNull
  public Collection<LombokProcessorData> getApplicableProcessors(@NotNull PsiMember psiMember) {
    Collection<LombokProcessorData> result = Collections.emptyList();
    if (verifyLombokAnnotationPresent(psiMember)) {
      result = new ArrayList<LombokProcessorData>();

      addApplicableProcessors(psiMember, result);
      final PsiClass psiClass = psiMember.getContainingClass();
      if (null != psiClass) {
        addApplicableProcessors(psiClass, result);
      }
    }
    return result;
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
