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
    return convertAccessLevelToJavaModifier(getAnnotationValue(psiAnnotation, "value"));
  }

  @Nullable
  public static String getAccessVisibility(@NotNull PsiAnnotation psiAnnotation) {
    return convertAccessLevelToJavaModifier(getAnnotationValue(psiAnnotation, "access"));
  }

  private static String getAnnotationValue(final PsiAnnotation psiAnnotation, final String parameterName) {
    return PsiAnnotationUtil.getAnnotationValue(psiAnnotation, parameterName, String.class);
  }

  @Nullable
  private static String convertAccessLevelToJavaModifier(String value) {
    if (null == value || value.isEmpty() || value.equals("PUBLIC")) {
      return PsiModifier.PUBLIC;
    }
    if (value.equals("MODULE")) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (value.equals("PROTECTED")) {
      return PsiModifier.PROTECTED;
    }
    if (value.equals("PACKAGE")) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (value.equals("PRIVATE")) {
      return PsiModifier.PRIVATE;
    }
    if (value.equals("NONE")) {
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
