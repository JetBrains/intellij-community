package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtil;
import lombok.AccessLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Plushnikov Michail
 */
public class LombokProcessorUtil {

  @Nullable
  public static String getMethodModifier(@NotNull PsiAnnotation psiAnnotation) {
    return convertAccessLevelToJavaModifier(PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "value"));
  }

  @Nullable
  public static String getAccessVisibility(@NotNull PsiAnnotation psiAnnotation) {
    return convertAccessLevelToJavaModifier(PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "access"));
  }
  
  @NotNull
  public static Collection<String> getOnX(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameterName) {
    PsiAnnotationMemberValue onXValue = psiAnnotation.findAttributeValue(parameterName);
    if (!(onXValue instanceof PsiAnnotation)) {
      return Collections.emptyList();
    }
    Collection<PsiAnnotation> annotations = PsiAnnotationUtil.getAnnotationValues((PsiAnnotation) onXValue, "value", PsiAnnotation.class);
    Collection<String> annotationStrings = new ArrayList<String>();
    for (PsiAnnotation annotation : annotations) {
      PsiAnnotationParameterList params = annotation.getParameterList();
      annotationStrings.add(PsiAnnotationUtil.getSimpleNameOf(annotation) + params.getText());
    }
    return annotationStrings;
  }

  @Nullable
  private static String convertAccessLevelToJavaModifier(String value) {
    if (null == value || value.isEmpty() || "PUBLIC".equals(value)) {
      return PsiModifier.PUBLIC;
    }
    if ("MODULE".equals(value)) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if ("PROTECTED".equals(value)) {
      return PsiModifier.PROTECTED;
    }
    if ("PACKAGE".equals(value)) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if ("PRIVATE".equals(value)) {
      return PsiModifier.PRIVATE;
    }
    if ("NONE".equals(value)) {
      return null;
    } else {
      return null;
    }
  }

  @NotNull
  public static PsiAnnotation createAnnotationWithAccessLevel(@NotNull Class<? extends Annotation> annotationClass, @NotNull PsiModifierListOwner psiModifierListOwner) {
    String value = "";
    final PsiModifierList modifierList = psiModifierListOwner.getModifierList();
    if (null != modifierList) {
      final String accessModifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(modifierList));
      if (null != accessModifier) {
        final AccessLevel accessLevel = convertModifierToAccessLevel(accessModifier);
        if (null != accessLevel && !AccessLevel.PUBLIC.equals(accessLevel)) {
          value = AccessLevel.class.getName() + "." + accessLevel;
        }
      }
    }

    return PsiAnnotationUtil.createPsiAnnotation(psiModifierListOwner, annotationClass, value);
  }

  @Nullable
  public static AccessLevel convertModifierToAccessLevel(String psiModifier) {
    Map<String, AccessLevel> map = new HashMap<String, AccessLevel>();
    map.put(PsiModifier.PUBLIC, AccessLevel.PUBLIC);
    map.put(PsiModifier.PACKAGE_LOCAL, AccessLevel.PACKAGE);
    map.put(PsiModifier.PROTECTED, AccessLevel.PROTECTED);
    map.put(PsiModifier.PRIVATE, AccessLevel.PRIVATE);
    return map.get(psiModifier);
  }
}
