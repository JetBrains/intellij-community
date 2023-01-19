package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.util.ArrayUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.thirdparty.CapitalizationStrategy;
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
  public static final AccessorsInfo DEFAULT = new AccessorsInfo(false, false, false,
                                                                false, CapitalizationStrategy.defaultValue());
  private static final String CHAIN_VALUE = "chain";
  private static final String FLUENT_VALUE = "fluent";
  private static final String MAKE_FINAL_VALUE = "makeFinal";
  private static final String PREFIX_VALUE = "prefix";

  private final boolean fluent;
  private final boolean chain;
  private final boolean makeFinal;
  private final CapitalizationStrategy capitalizationStrategy;
  private final boolean doNotUseIsPrefix;
  private final String[] prefixes;

  private AccessorsInfo(boolean fluentValue, boolean chainValue, boolean makeFinal,
                        boolean doNotUseIsPrefix, CapitalizationStrategy capitalizationStrategy,
                        String... prefixes) {
    this.fluent = fluentValue;
    this.chain = chainValue;
    this.makeFinal = makeFinal;
    this.doNotUseIsPrefix = doNotUseIsPrefix;
    this.capitalizationStrategy = capitalizationStrategy;
    this.prefixes = null == prefixes ? ArrayUtil.EMPTY_STRING_ARRAY : prefixes;
  }

  @NotNull
  public static AccessorsInfo build(boolean fluentValue,
                                    boolean chainValue,
                                    boolean makeFinal,
                                    boolean doNotUseIsPrefix,
                                    CapitalizationStrategy capitalizationStrategy,
                                    String... prefixes) {
    return new AccessorsInfo(fluentValue, chainValue, makeFinal, doNotUseIsPrefix, capitalizationStrategy, prefixes);
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
    final CapitalizationStrategy capitalizationStrategy;
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

      final String capitalizationStrategyValue = configDiscovery.getStringLombokConfigProperty(ConfigKey.ACCESSORS_JAVA_BEANS_SPEC_CAPITALIZATION, psiClass);
      capitalizationStrategy = CapitalizationStrategy.convertValue(capitalizationStrategyValue);
    }
    else {
      isFluent = null != fluentDeclaredValue && fluentDeclaredValue;
      isChained = null != chainDeclaredValue && chainDeclaredValue;
      makeFinal = null != makeFinalDeclaredValue && makeFinalDeclaredValue;
      prefixes = ArrayUtil.toStringArray(prefixDeclared);
      doNotUseIsPrefix = false;
      capitalizationStrategy = CapitalizationStrategy.defaultValue();
    }

    boolean isChainDeclaredOrImplicit = isChained || (isFluent && null == chainDeclaredValue);
    return build(isFluent, isChainDeclaredOrImplicit, makeFinal, doNotUseIsPrefix, capitalizationStrategy, prefixes);
  }

  public record AccessorsValues(Boolean chainDeclaredValue, Boolean fluentDeclaredValue, Boolean makeFinalDeclaredValue,
                                Collection<String> prefixes) {

    private AccessorsValues() {
      this(null, null, null, Collections.emptyList());
    }

    private AccessorsValues combine(AccessorsValues defaults) {
      Boolean combinedChain = chainDeclaredValue;
      Boolean combinedFluent = fluentDeclaredValue;
      Boolean combinedMakeFinal = makeFinalDeclaredValue;
      Collection<String> combinedPrefixes = prefixes;

      if (combinedChain == null && null != defaults.chainDeclaredValue) {
        combinedChain = defaults.chainDeclaredValue;
      }
      if (combinedFluent == null && null != defaults.fluentDeclaredValue) {
        combinedFluent = defaults.fluentDeclaredValue;
      }
      if (combinedMakeFinal == null && null != defaults.makeFinalDeclaredValue) {
        combinedMakeFinal = defaults.makeFinalDeclaredValue;
      }
      if (combinedPrefixes.isEmpty() && !defaults.prefixes.isEmpty()) {
        combinedPrefixes = defaults.prefixes;
      }
      return new AccessorsValues(combinedChain, combinedFluent, combinedMakeFinal, combinedPrefixes);
    }
  }

  private static AccessorsValues collectValues(@NotNull PsiAnnotation accessorsAnnotation) {
    Boolean chainDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, CHAIN_VALUE);
    Boolean fluentDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, FLUENT_VALUE);
    Boolean makeFinalDeclaredValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(accessorsAnnotation, MAKE_FINAL_VALUE);
    Collection<String> prefixes = PsiAnnotationUtil.getAnnotationValues(accessorsAnnotation, PREFIX_VALUE, String.class);
    return new AccessorsValues(chainDeclaredValue, fluentDeclaredValue, makeFinalDeclaredValue, prefixes);
  }

  private static AccessorsInfo buildFrom(@Nullable PsiClass psiClass, AccessorsValues values) {
    return buildAccessorsInfo(psiClass, values.chainDeclaredValue, values.fluentDeclaredValue, values.makeFinalDeclaredValue,
                              values.prefixes);
  }

  @NotNull
  public static AccessorsInfo buildFor(@NotNull PsiField psiField) {
    final AccessorsValues fieldAccessorsValues = getAccessorsValues(psiField);
    final AccessorsValues classAccessorsValues = getAccessorsValues(psiField.getContainingClass());
    final AccessorsValues combinedAccessorValues = fieldAccessorsValues.combine(classAccessorsValues);

    final PsiClass containingClass = psiField.getContainingClass();
    return buildFrom(containingClass, combinedAccessorValues);
  }

  @NotNull
  public static AccessorsInfo buildFor(@NotNull PsiClass psiClass) {
    AccessorsValues resultAccessorsValues = getAccessorsValues(psiClass);
    return buildFrom(psiClass, resultAccessorsValues);
  }

  @NotNull
  private static AccessorsValues getAccessorsValues(@NotNull PsiField psiField) {
    AccessorsValues accessorsValues = new AccessorsValues();
    final PsiAnnotation accessorsFieldAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.ACCESSORS);
    if (null != accessorsFieldAnnotation) {
      accessorsValues = collectValues(accessorsFieldAnnotation);
    }
    return accessorsValues;
  }

  @NotNull
  public static AccessorsValues getAccessorsValues(@Nullable PsiClass psiClass) {
    AccessorsValues resultAccessorsValues = new AccessorsValues();

    PsiClass containingClass = psiClass;
    while (null != containingClass) {
      final PsiAnnotation accessorsClassAnnotation = PsiAnnotationSearchUtil.findAnnotation(containingClass, LombokClassNames.ACCESSORS);
      if (null != accessorsClassAnnotation) {
        final AccessorsValues classAccessorsValues = collectValues(accessorsClassAnnotation);
        resultAccessorsValues = resultAccessorsValues.combine(classAccessorsValues);
      }
      containingClass = containingClass.getContainingClass();
    }
    return resultAccessorsValues;
  }

  @NotNull
  public static AccessorsInfo buildFor(@NotNull PsiField psiField, AccessorsValues classAccessorsValues) {
    final AccessorsValues fieldAccessorsValues = getAccessorsValues(psiField);
    final AccessorsValues combinedAccessorValues = fieldAccessorsValues.combine(classAccessorsValues);

    final PsiClass containingClass = psiField.getContainingClass();
    return buildFrom(containingClass, combinedAccessorValues);
  }

  public boolean isFluent() {
    return fluent;
  }

  public AccessorsInfo withFluent(boolean fluentValue) {
    if (fluent == fluentValue) {
      return this;
    }
    return build(fluentValue, chain, makeFinal, doNotUseIsPrefix, capitalizationStrategy, prefixes);
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

  public CapitalizationStrategy getCapitalizationStrategy() {
    return capitalizationStrategy;
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
