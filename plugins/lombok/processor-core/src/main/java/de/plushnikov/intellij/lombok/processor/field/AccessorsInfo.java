package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Date: 19.07.13 Time: 23:46
 *
 * @author Plushnikov Michail
 */
public class AccessorsInfo {
  private static final String ACCESSORS_ANNOTATION_NAME = Accessors.class.getName();

  private final boolean fluent;
  private final boolean chain;
  private final String[] prefixes;

  protected AccessorsInfo() {
    this(false, false);
  }

  protected AccessorsInfo(boolean fluentValue, boolean chainValue, String... prefixes) {
    this.fluent = fluentValue;
    this.chain = chainValue;
    this.prefixes = null == prefixes ? new String[0] : prefixes;
  }

  public static AccessorsInfo build(@NotNull PsiField psiField) {
    final PsiAnnotation accessorsFieldAnnotation = AnnotationUtil.findAnnotation(psiField, ACCESSORS_ANNOTATION_NAME);
    if (null != accessorsFieldAnnotation) {
      return buildFromAnnotation(accessorsFieldAnnotation);
    } else {
      PsiClass containingClass = psiField.getContainingClass();
      while (null != containingClass) {
        final PsiAnnotation accessorsClassAnnotation = AnnotationUtil.findAnnotation(containingClass, ACCESSORS_ANNOTATION_NAME);
        if (null != accessorsClassAnnotation) {
          return buildFromAnnotation(accessorsClassAnnotation);
        }
        containingClass = containingClass.getContainingClass();
      }
    }
    return new AccessorsInfo();
  }

  private static AccessorsInfo buildFromAnnotation(PsiAnnotation accessorsAnnotation) {
    Boolean fluentValue = PsiAnnotationUtil.getAnnotationValue(accessorsAnnotation, "fluent", Boolean.class);
    Boolean chainValue = PsiAnnotationUtil.getAnnotationValue(accessorsAnnotation, "chain", Boolean.class);
    Boolean chainDeclaredValue = PsiAnnotationUtil.getDeclaredAnnotationValue(accessorsAnnotation, "chain", Boolean.class);
    Collection<String> prefixes = PsiAnnotationUtil.getAnnotationValues(accessorsAnnotation, "prefix", String.class);

    boolean isFluent = null == fluentValue ? false : fluentValue;
    boolean isChained = null == chainValue ? false : chainValue;

    boolean isChainDeclaredOrImplicit = isChained || (isFluent && null == chainDeclaredValue);
    return new AccessorsInfo(isFluent, isChainDeclaredOrImplicit, prefixes.toArray(new String[prefixes.size()]));
  }

  public boolean isFluent() {
    return fluent;
  }

  public boolean isChain() {
    return chain;
  }

  public boolean prefixDefinedAndStartsWith(String fieldName) {
    if (prefixes.length == 0) {
      return true;
    }

    for (String prefix : prefixes) {
      if (canPrefixApply(fieldName, prefix)) {
        return true;
      }
    }
    return false;
  }

  public String removePrefix(String fieldName) {
    for (String prefix : prefixes) {
      if (canPrefixApply(fieldName, prefix)) {
        return fieldName.substring(prefix.length());
      }
    }
    return fieldName;
  }

  private boolean canPrefixApply(String fieldName, String prefix) {
    final int prefixLength = prefix.length();
    return fieldName.startsWith(prefix) && fieldName.length() > prefixLength &&
        (prefixLength == 0 || Character.isUpperCase(fieldName.charAt(prefixLength)));
  }
}
