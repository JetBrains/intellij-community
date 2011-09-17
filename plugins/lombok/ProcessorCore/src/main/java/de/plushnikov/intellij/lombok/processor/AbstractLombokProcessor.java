package de.plushnikov.intellij.lombok.processor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.light.LightMethod;
import de.plushnikov.intellij.lombok.psi.MyLightMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Base lombok processor class
 *
 * @author Plushnikov Michail
 */
public class AbstractLombokProcessor implements LombokProcessor {
  /**
   * Anntotation qualified name this processor supports
   */
  private final String supportedAnnotation;
  /**
   * Kind of output elements this processor supports
   */
  private final Class supportedClass;

  /**
   * Constructor for all Lombok-Processors
   *
   * @param supportedAnnotation anntotation qualified name this processor supports
   * @param supportedClass      kind of output elements this processor supports
   */
  protected AbstractLombokProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    this.supportedAnnotation = supportedAnnotation;
    this.supportedClass = supportedClass;
  }

  public <Psi extends PsiElement> boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<Psi> type) {
    final String annotationName = StringUtil.notNullize(psiAnnotation.getQualifiedName()).trim();
    return (supportedAnnotation.equals(annotationName) || supportedAnnotation.endsWith(annotationName)) &&
        (type.isAssignableFrom(supportedClass));
  }

  @NotNull
  protected LightMethod prepareMethod(@NotNull PsiManager manager, @NotNull PsiMethod method, @NotNull PsiClass psiClass, @NotNull PsiElement psiNavigationTarget) {
    LightMethod lightMethod = new MyLightMethod(manager, method, psiClass);
    lightMethod.setNavigationElement(psiNavigationTarget);
    return lightMethod;
  }

  protected boolean hasMethodByName(@NotNull PsiMethod[] classMethods, @NotNull String methodName) {
    boolean hasMethod = false;
    for (PsiMethod classMethod : classMethods) {
      if (classMethod.getName().equals(methodName)) {
        hasMethod = true;
        break;
      }
    }
    return hasMethod;
  }

  protected boolean hasMethodByName(@NotNull PsiMethod[] classMethods, String... methodNames) {
    boolean hasMethod = false;
    for (String methodName : methodNames) {
      if (hasMethodByName(classMethods, methodName)) {
        hasMethod = true;
        break;
      }
    }
    return hasMethod;
  }

  protected boolean hasMethodByName(@NotNull PsiMethod[] classMethods, @NotNull Collection<String> methodNames) {
    boolean hasMethod = false;
    for (String methodName : methodNames) {
      if (hasMethodByName(classMethods, methodName)) {
        hasMethod = true;
        break;
      }
    }
    return hasMethod;
  }

  @NotNull
  protected Collection<PsiField> filterFieldsByModifiers(@NotNull PsiField[] psiFields, String... modifiers) {
    Collection<PsiField> filterdFields = new ArrayList<PsiField>(psiFields.length);
    for (PsiField psiField : psiFields) {
      boolean addField = true;

      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        for (String modifier : modifiers) {
          addField &= !modifierList.hasModifierProperty(modifier);
        }
      }

      if (addField) {
        filterdFields.add(psiField);
      }
    }
    return filterdFields;
  }

  @NotNull
  protected Collection<PsiField> filterFieldsByNames(@NotNull PsiField[] psiFields, String... fieldNames) {
    Collection<PsiField> filterdFields = new ArrayList<PsiField>(psiFields.length);
    for (PsiField psiField : psiFields) {
      boolean addField = true;

      final String psiFieldName = psiField.getName();
      for (String fieldName : fieldNames) {
        addField &= !psiFieldName.equals(fieldName);
      }

      if (addField) {
        filterdFields.add(psiField);
      }
    }
    return filterdFields;
  }
}
