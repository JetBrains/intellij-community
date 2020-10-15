package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.LombokNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Plushnikov Michail
 */
public class LombokProcessorUtil {

  private static final Map<Integer, String> ACCESS_LEVEL_MAP = new HashMap<Integer, String>() {{
    put(PsiUtil.ACCESS_LEVEL_PUBLIC, LombokNames.ACCESS_LEVEL_PUBLIC);
    put(PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, LombokNames.ACCESS_LEVEL_PACKAGE_LOCAL);
    put(PsiUtil.ACCESS_LEVEL_PROTECTED, LombokNames.ACCESS_LEVEL_PROTECTED);
    put(PsiUtil.ACCESS_LEVEL_PRIVATE, LombokNames.ACCESS_LEVEL_PRIVATE);
  }};

  private static final Map<String, String> VALUE_ACCESS_LEVEL_MAP = new HashMap<String, String>() {{
    put(LombokNames.ACCESS_LEVEL_PUBLIC, PsiModifier.PUBLIC);
    put(LombokNames.ACCESS_LEVEL_PACKAGE_LOCAL, PsiModifier.PACKAGE_LOCAL);
    put(LombokNames.ACCESS_LEVEL_PROTECTED, PsiModifier.PROTECTED);
    put(LombokNames.ACCESS_LEVEL_PRIVATE, PsiModifier.PRIVATE);
  }};

  @Nullable
  @PsiModifier.ModifierConstant
  public static String getMethodModifier(@NotNull PsiAnnotation psiAnnotation) {
    return getLevelVisibility(psiAnnotation, "value");
  }

  @Nullable
  @PsiModifier.ModifierConstant
  public static String getAccessVisibility(@NotNull PsiAnnotation psiAnnotation) {
    return getLevelVisibility(psiAnnotation, "access");
  }

  @Nullable
  @PsiModifier.ModifierConstant
  public static String getLevelVisibility(@NotNull PsiAnnotation psiAnnotation) {
    return getLevelVisibility(psiAnnotation, "level");
  }

  @Nullable
  @PsiModifier.ModifierConstant
  private static String getLevelVisibility(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter) {
    return convertAccessLevelToJavaModifier(PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, parameter));
  }

  @Nullable
  public static String getAccessLevel(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameter) {
    final String annotationValue = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, parameter);
    return annotationValue == null ? null : VALUE_ACCESS_LEVEL_MAP.get(annotationValue);
  }

  public static boolean isLevelVisible(@NotNull PsiAnnotation psiAnnotation) {
    return null != getLevelVisibility(psiAnnotation);
  }

  public static Collection<String> getOnX(@NotNull PsiAnnotation psiAnnotation, @NotNull String parameterName) {
    PsiAnnotationMemberValue onXValue = psiAnnotation.findAttributeValue(parameterName);
    if (!(onXValue instanceof PsiAnnotation)) {
      return Collections.emptyList();
    }
    Collection<PsiAnnotation> annotations = PsiAnnotationUtil.getAnnotationValues((PsiAnnotation) onXValue, "value", PsiAnnotation.class);
    Collection<String> annotationStrings = new ArrayList<>();
    for (PsiAnnotation annotation : annotations) {
      PsiAnnotationParameterList params = annotation.getParameterList();
      annotationStrings.add(PsiAnnotationSearchUtil.getSimpleNameOf(annotation) + params.getText());
    }
    return annotationStrings;
  }

  @Nullable
  @PsiModifier.ModifierConstant
  private static String convertAccessLevelToJavaModifier(String value) {
    if (null == value || value.isEmpty()) {
      return PsiModifier.PUBLIC;
    }

    if ("PUBLIC".equals(value)) {
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
    }
    return null;
  }

  @NotNull
  public static PsiAnnotation createAnnotationWithAccessLevel(@NotNull PsiModifierListOwner psiModifierListOwner,
                                                              String annotationClassName) {
    String value = "";
    final PsiModifierList modifierList = psiModifierListOwner.getModifierList();
    if (null != modifierList) {
      final int accessLevelCode = PsiUtil.getAccessLevel(modifierList);

      final String accessLevel = ACCESS_LEVEL_MAP.get(accessLevelCode);
      if (null != accessLevel && !LombokNames.ACCESS_LEVEL_PUBLIC.equals(accessLevel)) {
        value = LombokNames.ACCESS_LEVEL + "." + accessLevel;
      }
    }

    return PsiAnnotationUtil.createPsiAnnotation(psiModifierListOwner, value, annotationClassName);
  }
}
