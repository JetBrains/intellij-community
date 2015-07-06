package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKeys;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Plushnikov Michail
 */
public class AccessorsInfo {
  private static final String ACCESSORS_ANNOTATION_NAME = Accessors.class.getName();
  public static final AccessorsInfo EMPTY = new AccessorsInfo(false, false, false);

  private final boolean fluent;
  private final boolean chain;
  private final String[] prefixes;
  private final boolean dontUseIsPrefix;

  protected AccessorsInfo(boolean fluentValue, boolean chainValue, boolean dontUseIsPrefix, String... prefixes) {
    this.fluent = fluentValue;
    this.chain = chainValue;
    this.dontUseIsPrefix = dontUseIsPrefix;
    this.prefixes = null == prefixes ? new String[0] : prefixes;
  }

  public static AccessorsInfo build(boolean fluentValue, boolean chainValue, boolean dontUseIsPrefix, String... prefixes) {
    return new AccessorsInfo(fluentValue, chainValue, dontUseIsPrefix, prefixes);
  }

  public static AccessorsInfo build(@NotNull PsiField psiField) {
    final PsiAnnotation accessorsFieldAnnotation = PsiAnnotationUtil.findAnnotation(psiField, ACCESSORS_ANNOTATION_NAME);
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != accessorsFieldAnnotation) {
      return buildFromAnnotation(accessorsFieldAnnotation, containingClass);
    } else {
      return build(containingClass);
    }
  }

  public static AccessorsInfo build(@Nullable PsiClass psiClass) {
    PsiClass containingClass = psiClass;
    while (null != containingClass) {
      final PsiAnnotation accessorsClassAnnotation = PsiAnnotationUtil.findAnnotation(containingClass, ACCESSORS_ANNOTATION_NAME);
      if (null != accessorsClassAnnotation) {
        return buildFromAnnotation(accessorsClassAnnotation, containingClass);
      }
      containingClass = containingClass.getContainingClass();
    }
    return build(false, false, false);
  }

  private static AccessorsInfo buildFromAnnotation(PsiAnnotation accessorsAnnotation, PsiClass psiClass) {
    final boolean isFluent = AbstractProcessor.readAnnotationOrConfigProperty(accessorsAnnotation, psiClass, "fluent", ConfigKeys.ACCESSORS_FLUENT);
    final boolean isChained = AbstractProcessor.readAnnotationOrConfigProperty(accessorsAnnotation, psiClass, "chain", ConfigKeys.ACCESSORS_CHAIN);

    Boolean chainDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, "chain");
    Collection<String> prefixes = PsiAnnotationUtil.getAnnotationValues(accessorsAnnotation, "prefix", String.class);

    final boolean dontUseIsPrefix = ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(ConfigKeys.GETTER_NO_IS_PREFIX, psiClass);

    boolean isChainDeclaredOrImplicit = isChained || (isFluent && null == chainDeclaredValue);
    return new AccessorsInfo(isFluent, isChainDeclaredOrImplicit, dontUseIsPrefix, prefixes.toArray(new String[prefixes.size()]));
  }

  public boolean isFluent() {
    return fluent;
  }

  public boolean isChain() {
    return chain;
  }

  public boolean isDontUseIsPrefix() {
    return dontUseIsPrefix;
  }

  public String[] getPrefixes() {
    return prefixes;
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
        return StringUtil.decapitalize(fieldName.substring(prefix.length()));
      }
    }
    return fieldName;
  }

  private boolean canPrefixApply(String fieldName, String prefix) {
    final int prefixLength = prefix.length();
    return fieldName.startsWith(prefix) && fieldName.length() > prefixLength &&
        (prefixLength == 0 || Character.isUpperCase(fieldName.charAt(prefixLength)) || !Character.isLetter(fieldName.charAt(prefixLength - 1)));
  }
}
