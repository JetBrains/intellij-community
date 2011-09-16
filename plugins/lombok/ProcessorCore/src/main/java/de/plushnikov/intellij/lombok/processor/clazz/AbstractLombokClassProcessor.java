package de.plushnikov.intellij.lombok.processor.clazz;


import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.light.LightMethod;
import de.plushnikov.intellij.lombok.psi.MyLightMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokClassProcessor implements LombokClassProcessor {

  private final String supportedAnnotation;
  private final Class supportedClass;

  protected AbstractLombokClassProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    this.supportedAnnotation = supportedAnnotation;
    this.supportedClass = supportedClass;
  }

  public boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class type) {
    final String annotationName = StringUtil.notNullize(psiAnnotation.getQualifiedName()).trim();
    return (supportedAnnotation.equals(annotationName) || supportedAnnotation.endsWith(annotationName))
        && type.isAssignableFrom(supportedClass);
  }

  protected LightMethod prepareMethod(@NotNull PsiManager manager, @NotNull PsiMethod method, @NotNull PsiClass psiClass, @NotNull PsiElement psiNavigationTarget) {
    LightMethod lightMethod = new MyLightMethod(manager, method, psiClass);
    lightMethod.setNavigationElement(psiNavigationTarget);
    return lightMethod;
  }

}
