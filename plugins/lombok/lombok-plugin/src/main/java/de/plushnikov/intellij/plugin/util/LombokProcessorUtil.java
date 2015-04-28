package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtil;
import lombok.AccessLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Plushnikov Michail
 */
public class LombokProcessorUtil {

  @Nullable
  public static String getMethodModifier(@NotNull PsiAnnotation psiAnnotation) {
    return convertAccessLevelToJavaModifier(PsiAnnotationUtil.getAccessLevelAnnotationValue(psiAnnotation, "value"));
  }

  @Nullable
  public static String getAccessVisibility(@NotNull PsiAnnotation psiAnnotation) {
    return convertAccessLevelToJavaModifier(PsiAnnotationUtil.getAccessLevelAnnotationValue(psiAnnotation, "access"));
  }

  @Nullable
  private static String convertAccessLevelToJavaModifier(AccessLevel value) {
    if (null == value || AccessLevel.PUBLIC.equals(value)) {
      return PsiModifier.PUBLIC;
    }
    if (AccessLevel.MODULE.equals(value)) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (AccessLevel.PROTECTED.equals(value)) {
      return PsiModifier.PROTECTED;
    }
    if (AccessLevel.PACKAGE.equals(value)) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (AccessLevel.PRIVATE.equals(value)) {
      return PsiModifier.PRIVATE;
    }
    if (AccessLevel.NONE.equals(value)) {
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
