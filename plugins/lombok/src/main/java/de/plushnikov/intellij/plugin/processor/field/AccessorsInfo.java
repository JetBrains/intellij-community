package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiVariable;
import com.intellij.util.ArrayUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Plushnikov Michail
 */
public class AccessorsInfo {
  public static final AccessorsInfo EMPTY = new AccessorsInfo(false, false, false, false);

  private final boolean fluent;
  private final boolean chain;
  private final boolean makeFinal;
  private final String[] prefixes;
  private final boolean doNotUseIsPrefix;

  private AccessorsInfo(boolean fluentValue, boolean chainValue, boolean makeFinal, boolean doNotUseIsPrefix, String... prefixes) {
    this.fluent = fluentValue;
    this.chain = chainValue;
    this.makeFinal = makeFinal;
    this.doNotUseIsPrefix = doNotUseIsPrefix;
    this.prefixes = null == prefixes ? ArrayUtil.EMPTY_STRING_ARRAY : prefixes;
  }

  @NotNull
  public static AccessorsInfo build(boolean fluentValue, boolean chainValue, boolean makeFinal, boolean doNotUseIsPrefix, String... prefixes) {
    return new AccessorsInfo(fluentValue, chainValue, makeFinal, doNotUseIsPrefix, prefixes);
  }

  @NotNull
  public static AccessorsInfo build(@NotNull PsiField psiField) {
    return build(psiField, psiField.getContainingClass());
  }

  @NotNull
  public static AccessorsInfo build(@NotNull PsiVariable psiVariable, @Nullable PsiClass containingClass) {
    final PsiAnnotation accessorsFieldAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiVariable, LombokClassNames.ACCESSORS);
    if (null != accessorsFieldAnnotation) {
      return buildFromAnnotation(accessorsFieldAnnotation, containingClass);
    }
    else {
      return build(containingClass);
    }
  }

  @NotNull
  public static AccessorsInfo build(@NotNull PsiField psiField, @NotNull AccessorsInfo classAccessorsInfo) {
    final PsiAnnotation accessorsFieldAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.ACCESSORS);
    if (null != accessorsFieldAnnotation) {
      return buildFromAnnotation(accessorsFieldAnnotation, psiField.getContainingClass());
    }
    else {
      return classAccessorsInfo;
    }
  }

  @NotNull
  public static AccessorsInfo build(@Nullable PsiClass psiClass) {
    PsiClass containingClass = psiClass;
    while (null != containingClass) {
      final PsiAnnotation accessorsClassAnnotation = PsiAnnotationSearchUtil.findAnnotation(containingClass, LombokClassNames.ACCESSORS);
      if (null != accessorsClassAnnotation) {
        return buildFromAnnotation(accessorsClassAnnotation, containingClass);
      }
      containingClass = containingClass.getContainingClass();
    }

    return buildAccessorsInfo(psiClass, null, null, null, Collections.emptySet());
  }

  @NotNull
  private static AccessorsInfo buildFromAnnotation(@NotNull PsiAnnotation accessorsAnnotation, @Nullable PsiClass psiClass) {
    Boolean chainDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, "chain");
    Boolean fluentDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, "fluent");
    Boolean makeFinal = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, "makeFinal");
    Collection<String> prefixes = PsiAnnotationUtil.getAnnotationValues(accessorsAnnotation, "prefix", String.class);

    return buildAccessorsInfo(psiClass, chainDeclaredValue, fluentDeclaredValue, makeFinal, prefixes);
  }

  @NotNull
  private static AccessorsInfo buildAccessorsInfo(@Nullable PsiClass psiClass, @Nullable Boolean chainDeclaredValue,
                                                  @Nullable Boolean fluentDeclaredValue,
                                                  @Nullable Boolean makeFinalDeclaredValue,
                                                  @NotNull Collection<String> prefixDeclared) {
    final boolean isFluent;
    final boolean isChained;
    final boolean makeFinal;
    final boolean doNotUseIsPrefix;
    final String[] prefixes;

    if (null != psiClass) {
      final ConfigDiscovery configDiscovery = ConfigDiscovery.getInstance();
      if (null == fluentDeclaredValue) {
        isFluent = configDiscovery.getBooleanLombokConfigProperty(ConfigKey.ACCESSORS_FLUENT, psiClass);
      }
      else {
        isFluent = fluentDeclaredValue;
      }

      if (null == chainDeclaredValue) {
        isChained = configDiscovery.getBooleanLombokConfigProperty(ConfigKey.ACCESSORS_CHAIN, psiClass);
      }
      else {
        isChained = chainDeclaredValue;
      }

      if (null == makeFinalDeclaredValue) {
        makeFinal = configDiscovery.getBooleanLombokConfigProperty(ConfigKey.ACCESSORS_MAKE_FINAL, psiClass);
      }
      else {
        makeFinal = makeFinalDeclaredValue;
      }

      if (prefixDeclared.isEmpty()) {
        prefixes = ArrayUtil.toStringArray(configDiscovery.getMultipleValueLombokConfigProperty(ConfigKey.ACCESSORS_PREFIX, psiClass));
      }
      else {
        prefixes = ArrayUtil.toStringArray(prefixDeclared);
      }

      doNotUseIsPrefix = configDiscovery.getBooleanLombokConfigProperty(ConfigKey.GETTER_NO_IS_PREFIX, psiClass);
    }
    else {
      isFluent = null != fluentDeclaredValue && fluentDeclaredValue;
      isChained = null != chainDeclaredValue && chainDeclaredValue;
      makeFinal = null != makeFinalDeclaredValue && makeFinalDeclaredValue;
      prefixes = ArrayUtil.toStringArray(prefixDeclared);
      doNotUseIsPrefix = false;
    }

    boolean isChainDeclaredOrImplicit = isChained || (isFluent && null == chainDeclaredValue);
    return build(isFluent, isChainDeclaredOrImplicit, makeFinal, doNotUseIsPrefix, prefixes);
  }

  public boolean isFluent() {
    return fluent;
  }

  public AccessorsInfo withFluent(boolean fluentValue) {
    if (fluent == fluentValue) {
      return this;
    }
    return build(fluentValue, chain, makeFinal, doNotUseIsPrefix, prefixes);
  }

  public boolean isChain() {
    return chain;
  }

  public boolean isMakeFinal() {
    return makeFinal;
  }

  public boolean isDoNotUseIsPrefix() {
    return doNotUseIsPrefix;
  }

  public String[] getPrefixes() {
    return prefixes;
  }

  public boolean isPrefixUnDefinedOrNotStartsWith(String fieldName) {
    if (prefixes.length == 0) {
      return false;
    }

    for (String prefix : prefixes) {
      if (canPrefixApply(fieldName, prefix)) {
        return false;
      }
    }
    return true;
  }

  public String removePrefix(String fieldName) {
    for (String prefix : prefixes) {
      if (canPrefixApply(fieldName, prefix)) {
        return prefix.isEmpty() ? fieldName : decapitalizeLikeLombok(fieldName.substring(prefix.length()));
      }
    }
    return fieldName;
  }

  private static boolean canPrefixApply(String fieldName, String prefix) {
    final int prefixLength = prefix.length();
    // we can use digits and upper case letters after a prefix, but not lower case letters
    return prefixLength == 0 ||
           fieldName.startsWith(prefix) && fieldName.length() > prefixLength &&
           (!Character.isLetter(prefix.charAt(prefix.length() - 1)) || !Character.isLowerCase(fieldName.charAt(prefixLength)));
  }

  private static String decapitalizeLikeLombok(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    char[] chars = name.toCharArray();
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
