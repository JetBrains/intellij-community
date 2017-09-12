package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Plushnikov Michail
 */
public class AccessorsInfo {
  public static final AccessorsInfo EMPTY = new AccessorsInfo(false, false, false);

  private final boolean fluent;
  private final boolean chain;
  private final String[] prefixes;
  private final boolean doNotUseIsPrefix;

  private AccessorsInfo(boolean fluentValue, boolean chainValue, boolean doNotUseIsPrefix, String... prefixes) {
    this.fluent = fluentValue;
    this.chain = chainValue;
    this.doNotUseIsPrefix = doNotUseIsPrefix;
    this.prefixes = null == prefixes ? new String[0] : prefixes;
  }

  @NotNull
  public static AccessorsInfo build(boolean fluentValue, boolean chainValue, boolean doNotUseIsPrefix, String... prefixes) {
    return new AccessorsInfo(fluentValue, chainValue, doNotUseIsPrefix, prefixes);
  }

  @NotNull
  public static AccessorsInfo build(@NotNull PsiField psiField) {
    final PsiAnnotation accessorsFieldAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, Accessors.class);
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != accessorsFieldAnnotation) {
      return buildFromAnnotation(accessorsFieldAnnotation, containingClass);
    } else {
      return build(containingClass);
    }
  }

  @NotNull
  public static AccessorsInfo build(@Nullable PsiClass psiClass) {
    PsiClass containingClass = psiClass;
    while (null != containingClass) {
      final PsiAnnotation accessorsClassAnnotation = PsiAnnotationSearchUtil.findAnnotation(containingClass, Accessors.class);
      if (null != accessorsClassAnnotation) {
        return buildFromAnnotation(accessorsClassAnnotation, containingClass);
      }
      containingClass = containingClass.getContainingClass();
    }

    return buildAccessorsInfo(psiClass, null, null, Collections.<String>emptySet());
  }

  @NotNull
  private static AccessorsInfo buildFromAnnotation(@NotNull PsiAnnotation accessorsAnnotation, @Nullable PsiClass psiClass) {
    Boolean chainDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, "chain");
    Boolean fluentDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, "fluent");
    Collection<String> prefixes = PsiAnnotationUtil.getAnnotationValues(accessorsAnnotation, "prefix", String.class);

    return buildAccessorsInfo(psiClass, chainDeclaredValue, fluentDeclaredValue, prefixes);
  }

  @NotNull
  private static AccessorsInfo buildAccessorsInfo(@Nullable PsiClass psiClass, @Nullable Boolean chainDeclaredValue,
                                                  @Nullable Boolean fluentDeclaredValue, @NotNull Collection<String> prefixDeclared) {
    final boolean isFluent;
    final boolean isChained;
    final boolean doNotUseIsPrefix;
    final String[] prefixes;

    if (null != psiClass) {
      if (null == fluentDeclaredValue) {
        isFluent = ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(ConfigKey.ACCESSORS_FLUENT, psiClass);
      } else {
        isFluent = fluentDeclaredValue;
      }

      if (null == chainDeclaredValue) {
        isChained = ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(ConfigKey.ACCESSORS_CHAIN, psiClass);
      } else {
        isChained = chainDeclaredValue;
      }

      if (prefixDeclared.isEmpty()) {
        prefixes = ConfigDiscovery.getInstance().getMultipleValueLombokConfigProperty(ConfigKey.ACCESSORS_PREFIX, psiClass);
      } else {
        prefixes = prefixDeclared.toArray(new String[prefixDeclared.size()]);
      }

      doNotUseIsPrefix = ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(ConfigKey.GETTER_NO_IS_PREFIX, psiClass);

    } else {
      isFluent = null == fluentDeclaredValue ? false : fluentDeclaredValue;
      isChained = null == chainDeclaredValue ? false : chainDeclaredValue;
      prefixes = prefixDeclared.toArray(new String[prefixDeclared.size()]);
      doNotUseIsPrefix = false;
    }

    boolean isChainDeclaredOrImplicit = isChained || (isFluent && null == chainDeclaredValue);
    return new AccessorsInfo(isFluent, isChainDeclaredOrImplicit, doNotUseIsPrefix, prefixes);
  }

  public boolean isFluent() {
    return fluent;
  }

  public AccessorsInfo withFluent(boolean fluentValue) {
    if (fluent == fluentValue) {
      return this;
    }
    return new AccessorsInfo(fluentValue, chain, doNotUseIsPrefix, prefixes);
  }

  public boolean isChain() {
    return chain;
  }

  public boolean isDoNotUseIsPrefix() {
    return doNotUseIsPrefix;
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
        return prefix.isEmpty() ? fieldName : decapitalizeLikeLombok(fieldName.substring(prefix.length()));
      }
    }
    return fieldName;
  }

  private boolean canPrefixApply(String fieldName, String prefix) {
    final int prefixLength = prefix.length();
    return prefixLength == 0 || fieldName.startsWith(prefix) && fieldName.length() > prefixLength &&
      (!Character.isLetter(prefix.charAt(prefix.length() - 1)) || Character.isUpperCase(fieldName.charAt(prefixLength)));
  }

  private String decapitalizeLikeLombok(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    char chars[] = name.toCharArray();
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
