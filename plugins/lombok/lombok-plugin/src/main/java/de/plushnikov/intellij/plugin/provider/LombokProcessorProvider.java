package de.plushnikov.intellij.plugin.provider;

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

  private final Map<String, Processor> lombokProcessors;
  private final Collection<String> registeredAnnotationNames;

  private LombokProcessorProvider() {
    lombokProcessors = new HashMap<String, Processor>();
    registeredAnnotationNames = new HashSet<String>();

    for (Processor processor : getLombokProcessors()) {
      Class<? extends Annotation> annotationClass = processor.getSupportedAnnotationClass();

      lombokProcessors.put(annotationClass.getName(), processor);
      lombokProcessors.put(annotationClass.getSimpleName(), processor);
    }

    registeredAnnotationNames.addAll(lombokProcessors.keySet());
  }

  @NotNull
  public Processor[] getLombokProcessors() {
    return LombokProcessorExtensionPoint.EP_NAME.getExtensions();
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
    assert psiModifierList != null;
    for (PsiAnnotation psiAnnotation : psiModifierList.getAnnotations()) {
      final Processor processor = lombokProcessors.get(psiAnnotation.getQualifiedName());
      if (null != processor) {
        target.add(new LombokProcessorData(processor, psiAnnotation));
      }
    }
  }
}
