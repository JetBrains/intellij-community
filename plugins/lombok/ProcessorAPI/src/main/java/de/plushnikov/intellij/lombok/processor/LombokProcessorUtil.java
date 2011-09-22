package de.plushnikov.intellij.lombok.processor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiKeyword;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class LombokProcessorUtil {

  @Nullable
  public static String getMethodVisibility(@NotNull PsiAnnotation psiAnnotation) {
    return convertAcessLevelToJavaString(PsiAnnotationUtil.<String>getAnnotationValue(psiAnnotation, "value"));
  }

  @Nullable
  public static String getAccessVisibity(@NotNull PsiAnnotation psiAnnotation) {
    return convertAcessLevelToJavaString(PsiAnnotationUtil.<String>getAnnotationValue(psiAnnotation, "access"));
  }

  @Nullable
  public static String convertAcessLevelToJavaString(String value) {
    if (null == value || value.isEmpty() || value.equals("PUBLIC"))
      return PsiKeyword.PUBLIC;
    if (value.equals("MODULE"))
      return "";
    if (value.equals("PROTECTED"))
      return PsiKeyword.PROTECTED;
    if (value.equals("PACKAGE"))
      return "";
    if (value.equals("PRIVATE"))
      return PsiKeyword.PRIVATE;
    if (value.equals("NONE"))
      return null;
    else
      return null;
  }
}
