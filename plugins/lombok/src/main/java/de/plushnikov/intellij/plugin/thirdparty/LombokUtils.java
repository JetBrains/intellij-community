package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author ProjectLombok Team
 * @author Plushnikov Michail
 */
public final class LombokUtils {
  public static final String LOMBOK_INTERN_FIELD_MARKER = "$";

  public static final String[] NONNULL_ANNOTATIONS = {
    "androidx.annotation.NonNull",
    "android.support.annotation.NonNull",
    "com.sun.istack.internal.NotNull",
    "edu.umd.cs.findbugs.annotations.NonNull",
    "javax.annotation.Nonnull",
    // "javax.validation.constraints.NotNull", // The field might contain a null value until it is persisted.
    "lombok.NonNull",
    "org.checkerframework.checker.nullness.qual.NonNull",
    "org.eclipse.jdt.annotation.NonNull",
    "org.eclipse.jgit.annotations.NonNull",
    "org.jetbrains.annotations.NotNull",
    "org.jmlspecs.annotation.NonNull",
    "org.netbeans.api.annotations.common.NonNull",
    "org.springframework.lang.NonNull"};

  static final String[] BASE_COPYABLE_ANNOTATIONS = {
    "androidx.annotation.NonNull",
    "androidx.annotation.Nullable",
    "android.support.annotation.NonNull",
    "android.support.annotation.Nullable",
    "edu.umd.cs.findbugs.annotations.NonNull",
    "edu.umd.cs.findbugs.annotations.Nullable",
    "edu.umd.cs.findbugs.annotations.UnknownNullness",
    "javax.annotation.CheckForNull",
    "javax.annotation.Nonnull",
    "javax.annotation.Nullable",
    "lombok.NonNull",
    "org.jmlspecs.annotation.NonNull",
    "org.jmlspecs.annotation.Nullable",

    "org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey",
    "org.checkerframework.checker.compilermsgs.qual.CompilerMessageKeyBottom",
    "org.checkerframework.checker.compilermsgs.qual.UnknownCompilerMessageKey",
    "org.checkerframework.checker.fenum.qual.AwtAlphaCompositingRule",
    "org.checkerframework.checker.fenum.qual.AwtColorSpace",
    "org.checkerframework.checker.fenum.qual.AwtCursorType",
    "org.checkerframework.checker.fenum.qual.AwtFlowLayout",
    "org.checkerframework.checker.fenum.qual.Fenum",
    "org.checkerframework.checker.fenum.qual.FenumBottom",
    "org.checkerframework.checker.fenum.qual.FenumTop",
    "org.checkerframework.checker.fenum.qual.PolyFenum",
    "org.checkerframework.checker.fenum.qual.SwingBoxOrientation",
    "org.checkerframework.checker.fenum.qual.SwingCompassDirection",
    "org.checkerframework.checker.fenum.qual.SwingElementOrientation",
    "org.checkerframework.checker.fenum.qual.SwingHorizontalOrientation",
    "org.checkerframework.checker.fenum.qual.SwingSplitPaneOrientation",
    "org.checkerframework.checker.fenum.qual.SwingTextOrientation",
    "org.checkerframework.checker.fenum.qual.SwingTitleJustification",
    "org.checkerframework.checker.fenum.qual.SwingTitlePosition",
    "org.checkerframework.checker.fenum.qual.SwingVerticalOrientation",
    "org.checkerframework.checker.formatter.qual.Format",
    "org.checkerframework.checker.formatter.qual.FormatBottom",
    "org.checkerframework.checker.formatter.qual.InvalidFormat",
    "org.checkerframework.checker.guieffect.qual.AlwaysSafe",
    "org.checkerframework.checker.guieffect.qual.PolyUI",
    "org.checkerframework.checker.guieffect.qual.UI",
    "org.checkerframework.checker.i18nformatter.qual.I18nFormat",
    "org.checkerframework.checker.i18nformatter.qual.I18nFormatBottom",
    "org.checkerframework.checker.i18nformatter.qual.I18nFormatFor",
    "org.checkerframework.checker.i18nformatter.qual.I18nInvalidFormat",
    "org.checkerframework.checker.i18nformatter.qual.I18nUnknownFormat",
    "org.checkerframework.checker.i18n.qual.LocalizableKey",
    "org.checkerframework.checker.i18n.qual.LocalizableKeyBottom",
    "org.checkerframework.checker.i18n.qual.Localized",
    "org.checkerframework.checker.i18n.qual.UnknownLocalizableKey",
    "org.checkerframework.checker.i18n.qual.UnknownLocalized",
    "org.checkerframework.checker.index.qual.GTENegativeOne",
    "org.checkerframework.checker.index.qual.IndexFor",
    "org.checkerframework.checker.index.qual.IndexOrHigh",
    "org.checkerframework.checker.index.qual.IndexOrLow",
    "org.checkerframework.checker.index.qual.LengthOf",
    "org.checkerframework.checker.index.qual.LessThan",
    "org.checkerframework.checker.index.qual.LessThanBottom",
    "org.checkerframework.checker.index.qual.LessThanUnknown",
    "org.checkerframework.checker.index.qual.LowerBoundBottom",
    "org.checkerframework.checker.index.qual.LowerBoundUnknown",
    "org.checkerframework.checker.index.qual.LTEqLengthOf",
    "org.checkerframework.checker.index.qual.LTLengthOf",
    "org.checkerframework.checker.index.qual.LTOMLengthOf",
    "org.checkerframework.checker.index.qual.NegativeIndexFor",
    "org.checkerframework.checker.index.qual.NonNegative",
    "org.checkerframework.checker.index.qual.PolyIndex",
    "org.checkerframework.checker.index.qual.PolyLength",
    "org.checkerframework.checker.index.qual.PolyLowerBound",
    "org.checkerframework.checker.index.qual.PolySameLen",
    "org.checkerframework.checker.index.qual.PolyUpperBound",
    "org.checkerframework.checker.index.qual.Positive",
    "org.checkerframework.checker.index.qual.SameLen",
    "org.checkerframework.checker.index.qual.SameLenBottom",
    "org.checkerframework.checker.index.qual.SameLenUnknown",
    "org.checkerframework.checker.index.qual.SearchIndexBottom",
    "org.checkerframework.checker.index.qual.SearchIndexFor",
    "org.checkerframework.checker.index.qual.SearchIndexUnknown",
    "org.checkerframework.checker.index.qual.SubstringIndexBottom",
    "org.checkerframework.checker.index.qual.SubstringIndexFor",
    "org.checkerframework.checker.index.qual.SubstringIndexUnknown",
    "org.checkerframework.checker.index.qual.UpperBoundBottom",
    "org.checkerframework.checker.index.qual.UpperBoundUnknown",
    "org.checkerframework.checker.initialization.qual.FBCBottom",
    "org.checkerframework.checker.initialization.qual.Initialized",
    "org.checkerframework.checker.initialization.qual.UnderInitialization",
    "org.checkerframework.checker.initialization.qual.UnknownInitialization",
    "org.checkerframework.checker.interning.qual.Interned",
    "org.checkerframework.checker.interning.qual.InternedDistinct",
    "org.checkerframework.checker.interning.qual.PolyInterned",
    "org.checkerframework.checker.interning.qual.UnknownInterned",
    "org.checkerframework.checker.lock.qual.GuardedBy",
    "org.checkerframework.checker.lock.qual.GuardedByBottom",
    "org.checkerframework.checker.lock.qual.GuardedByUnknown",
    "org.checkerframework.checker.lock.qual.GuardSatisfied",
    "org.checkerframework.checker.nullness.qual.KeyFor",
    "org.checkerframework.checker.nullness.qual.KeyForBottom",
    "org.checkerframework.checker.nullness.qual.MonotonicNonNull",
    "org.checkerframework.checker.nullness.qual.NonNull",
    "org.checkerframework.checker.nullness.qual.NonRaw",
    "org.checkerframework.checker.nullness.qual.Nullable",
    "org.checkerframework.checker.nullness.qual.PolyKeyFor",
    "org.checkerframework.checker.nullness.qual.PolyNull",
    "org.checkerframework.checker.nullness.qual.PolyRaw",
    "org.checkerframework.checker.nullness.qual.Raw",
    "org.checkerframework.checker.nullness.qual.UnknownKeyFor",
    "org.checkerframework.checker.optional.qual.MaybePresent",
    "org.checkerframework.checker.optional.qual.PolyPresent",
    "org.checkerframework.checker.optional.qual.Present",
    "org.checkerframework.checker.propkey.qual.PropertyKey",
    "org.checkerframework.checker.propkey.qual.PropertyKeyBottom",
    "org.checkerframework.checker.propkey.qual.UnknownPropertyKey",
    "org.checkerframework.checker.regex.qual.PolyRegex",
    "org.checkerframework.checker.regex.qual.Regex",
    "org.checkerframework.checker.regex.qual.RegexBottom",
    "org.checkerframework.checker.regex.qual.UnknownRegex",
    "org.checkerframework.checker.signature.qual.BinaryName",
    "org.checkerframework.checker.signature.qual.BinaryNameInUnnamedPackage",
    "org.checkerframework.checker.signature.qual.ClassGetName",
    "org.checkerframework.checker.signature.qual.ClassGetSimpleName",
    "org.checkerframework.checker.signature.qual.DotSeparatedIdentifiers",
    "org.checkerframework.checker.signature.qual.FieldDescriptor",
    "org.checkerframework.checker.signature.qual.FieldDescriptorForPrimitive",
    "org.checkerframework.checker.signature.qual.FieldDescriptorForPrimitiveOrArrayInUnnamedPackage",
    "org.checkerframework.checker.signature.qual.FqBinaryName",
    "org.checkerframework.checker.signature.qual.FullyQualifiedName",
    "org.checkerframework.checker.signature.qual.Identifier",
    "org.checkerframework.checker.signature.qual.IdentifierOrArray",
    "org.checkerframework.checker.signature.qual.InternalForm",
    "org.checkerframework.checker.signature.qual.MethodDescriptor",
    "org.checkerframework.checker.signature.qual.PolySignature",
    "org.checkerframework.checker.signature.qual.SignatureBottom",
    "org.checkerframework.checker.signedness.qual.Constant",
    "org.checkerframework.checker.signedness.qual.PolySignedness",
    "org.checkerframework.checker.signedness.qual.PolySigned",
    "org.checkerframework.checker.signedness.qual.Signed",
    "org.checkerframework.checker.signedness.qual.SignednessBottom",
    "org.checkerframework.checker.signedness.qual.SignednessGlb",
    "org.checkerframework.checker.signedness.qual.SignedPositive",
    "org.checkerframework.checker.signedness.qual.UnknownSignedness",
    "org.checkerframework.checker.signedness.qual.Unsigned",
    "org.checkerframework.checker.tainting.qual.PolyTainted",
    "org.checkerframework.checker.tainting.qual.Tainted",
    "org.checkerframework.checker.tainting.qual.Untainted",
    "org.checkerframework.checker.units.qual.A",
    "org.checkerframework.checker.units.qual.Acceleration",
    "org.checkerframework.checker.units.qual.Angle",
    "org.checkerframework.checker.units.qual.Area",
    "org.checkerframework.checker.units.qual.C",
    "org.checkerframework.checker.units.qual.cd",
    "org.checkerframework.checker.units.qual.Current",
    "org.checkerframework.checker.units.qual.degrees",
    "org.checkerframework.checker.units.qual.g",
    "org.checkerframework.checker.units.qual.h",
    "org.checkerframework.checker.units.qual.K",
    "org.checkerframework.checker.units.qual.kg",
    "org.checkerframework.checker.units.qual.km",
    "org.checkerframework.checker.units.qual.km2",
    "org.checkerframework.checker.units.qual.kmPERh",
    "org.checkerframework.checker.units.qual.Length",
    "org.checkerframework.checker.units.qual.Luminance",
    "org.checkerframework.checker.units.qual.m",
    "org.checkerframework.checker.units.qual.m2",
    "org.checkerframework.checker.units.qual.Mass",
    "org.checkerframework.checker.units.qual.min",
    "org.checkerframework.checker.units.qual.mm",
    "org.checkerframework.checker.units.qual.mm2",
    "org.checkerframework.checker.units.qual.mol",
    "org.checkerframework.checker.units.qual.mPERs",
    "org.checkerframework.checker.units.qual.mPERs2",
    "org.checkerframework.checker.units.qual.PolyUnit",
    "org.checkerframework.checker.units.qual.radians",
    "org.checkerframework.checker.units.qual.s",
    "org.checkerframework.checker.units.qual.Speed",
    "org.checkerframework.checker.units.qual.Substance",
    "org.checkerframework.checker.units.qual.Temperature",
    "org.checkerframework.checker.units.qual.Time",
    "org.checkerframework.checker.units.qual.UnitsBottom",
    "org.checkerframework.checker.units.qual.UnknownUnits",
    "org.checkerframework.common.aliasing.qual.LeakedToResult",
    "org.checkerframework.common.aliasing.qual.MaybeAliased",
    "org.checkerframework.common.aliasing.qual.NonLeaked",
    "org.checkerframework.common.aliasing.qual.Unique",
    "org.checkerframework.common.reflection.qual.ClassBound",
    "org.checkerframework.common.reflection.qual.ClassVal",
    "org.checkerframework.common.reflection.qual.ClassValBottom",
    "org.checkerframework.common.reflection.qual.MethodVal",
    "org.checkerframework.common.reflection.qual.MethodValBottom",
    "org.checkerframework.common.reflection.qual.UnknownClass",
    "org.checkerframework.common.reflection.qual.UnknownMethod",
    "org.checkerframework.common.subtyping.qual.Bottom",
    "org.checkerframework.common.util.report.qual.ReportUnqualified",
    "org.checkerframework.common.value.qual.ArrayLen",
    "org.checkerframework.common.value.qual.ArrayLenRange",
    "org.checkerframework.common.value.qual.BoolVal",
    "org.checkerframework.common.value.qual.BottomVal",
    "org.checkerframework.common.value.qual.DoubleVal",
    "org.checkerframework.common.value.qual.IntRange",
    "org.checkerframework.common.value.qual.IntVal",
    "org.checkerframework.common.value.qual.MinLen",
    "org.checkerframework.common.value.qual.PolyValue",
    "org.checkerframework.common.value.qual.StringVal",
    "org.checkerframework.common.value.qual.UnknownVal",
    "org.checkerframework.framework.qual.PolyAll",
    "org.checkerframework.framework.util.PurityUnqualified",

    "org.eclipse.jdt.annotation.NonNull",
    "org.eclipse.jdt.annotation.Nullable",
    "org.jetbrains.annotations.NotNull",
    "org.jetbrains.annotations.Nullable",
    "org.springframework.lang.NonNull",
    "org.springframework.lang.Nullable",
    "org.netbeans.api.annotations.common.NonNull",
    "org.netbeans.api.annotations.common.NullAllowed"};

  static final String[] COPY_TO_SETTER_ANNOTATIONS = {
    "com.fasterxml.jackson.annotation.JacksonInject",
    "com.fasterxml.jackson.annotation.JsonAlias",
    "com.fasterxml.jackson.annotation.JsonFormat",
    "com.fasterxml.jackson.annotation.JsonIgnore",
    "com.fasterxml.jackson.annotation.JsonIgnoreProperties",
    "com.fasterxml.jackson.annotation.JsonProperty",
    "com.fasterxml.jackson.annotation.JsonSetter",
    "com.fasterxml.jackson.annotation.JsonSubTypes",
    "com.fasterxml.jackson.annotation.JsonTypeInfo",
    "com.fasterxml.jackson.annotation.JsonView",
    "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
    "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty"};

  static final String[] COPY_TO_BUILDER_SINGULAR_SETTER_ANNOTATIONS = {
    "com.fasterxml.jackson.annotation.JsonAnySetter"};

  static final String[] JACKSON_COPY_TO_BUILDER_ANNOTATIONS = {
    "com.fasterxml.jackson.annotation.JsonAutoDetect",
    "com.fasterxml.jackson.annotation.JsonFormat",
    "com.fasterxml.jackson.annotation.JsonIgnoreProperties",
    "com.fasterxml.jackson.annotation.JsonIgnoreType",
    "com.fasterxml.jackson.annotation.JsonPropertyOrder",
    "com.fasterxml.jackson.annotation.JsonRootName",
    "com.fasterxml.jackson.annotation.JsonSubTypes",
    "com.fasterxml.jackson.annotation.JsonTypeInfo",
    "com.fasterxml.jackson.annotation.JsonTypeName",
    "com.fasterxml.jackson.annotation.JsonView",
    "com.fasterxml.jackson.databind.annotation.JsonNaming"};

  public static String getGetterName(final @NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    return getGetterName(psiField, accessorsInfo);
  }

  public static String getGetterName(@NotNull PsiField psiField, AccessorsInfo accessorsInfo) {
    final String psiFieldName = psiField.getName();
    final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());

    return toGetterName(accessorsInfo, psiFieldName, isBoolean);
  }

  public static String getSetterName(@NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    return getSetterName(psiField, accessorsInfo);
  }

  public static String getSetterName(@NotNull PsiField psiField, AccessorsInfo accessorsInfo) {
    return toSetterName(accessorsInfo, psiField.getName(), PsiType.BOOLEAN.equals(psiField.getType()));
  }

  /**
   * Generates a getter name from a given field name.
   * <p/>
   * Strategy:
   * <ul>
   * <li>Reduce the field's name to its base name by stripping off any prefix (from {@code Accessors}). If the field name does not fit
   * the prefix list, this method immediately returns {@code null}.</li>
   * <li>If {@code Accessors} has {@code fluent=true}, then return the basename.</li>
   * <li>Pick a prefix. 'get' normally, but 'is' if {@code isBoolean} is true.</li>
   * <li>Only if {@code isBoolean} is true: Check if the field starts with {@code is} followed by a non-lowercase character. If so, return the field name verbatim.</li>
   * <li>Check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.</li>
   * <li>Return the prefix plus the possibly title/uppercased first character, and the rest of the field name.</li>
   * </ul>
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type {@code java.lang.Boolean}, you should provide {@code false}.
   * @return The getter name for this field, or {@code null} if this field does not fit expected patterns and therefore cannot be turned into a getter name.
   */
  public static String toGetterName(AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAccessorName(accessors, fieldName, isBoolean, "is", "get");
  }

  /**
   * Generates a setter name from a given field name.
   * <p/>
   * Strategy:
   * <ul>
   * <li>Reduce the field's name to its base name by stripping off any prefix (from {@code Accessors}). If the field name does not fit
   * the prefix list, this method immediately returns {@code null}.</li>
   * <li>If {@code Accessors} has {@code fluent=true}, then return the basename.</li>
   * <li>Only if {@code isBoolean} is true: Check if the field starts with {@code is} followed by a non-lowercase character.
   * If so, replace {@code is} with {@code set} and return that.</li>
   * <li>Check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.</li>
   * <li>Return {@code "set"} plus the possibly title/uppercased first character, and the rest of the field name.</li>
   * </ul>
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type {@code java.lang.Boolean}, you should provide {@code false}.
   * @return The setter name for this field, or {@code null} if this field does not fit expected patterns and therefore cannot be turned into a getter name.
   */
  public static String toSetterName(AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAccessorName(accessors, fieldName, isBoolean, "set", "set");
  }

  /**
   * Generates a wither name from a given field name.
   * <p/>
   * Strategy:
   * <ul>
   * <li>Reduce the field's name to its base name by stripping off any prefix (from {@code Accessors}). If the field name does not fit
   * the prefix list, this method immediately returns {@code null}.</li>
   * <li>Only if {@code isBoolean} is true: Check if the field starts with {@code is} followed by a non-lowercase character.
   * If so, replace {@code is} with {@code with} and return that.</li>
   * <li>Check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.</li>
   * <li>Return {@code "with"} plus the possibly title/uppercased first character, and the rest of the field name.</li>
   * </ul>
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type {@code java.lang.Boolean}, you should provide {@code false}.
   * @return The wither name for this field, or {@code null} if this field does not fit expected patterns and therefore cannot be turned into a getter name.
   */
  public static String toWitherName(AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    if (accessors.isFluent()) {
      throw new IllegalArgumentException("@Wither does not support @Accessors(fluent=true)");
    }
    return toAccessorName(accessors, fieldName, isBoolean, "with", "with");
  }

  private static String toAccessorName(AccessorsInfo accessorsInfo,
                                       String fieldName,
                                       boolean isBoolean,
                                       String booleanPrefix,
                                       String normalPrefix) {
    final String result;

    fieldName = accessorsInfo.removePrefix(fieldName);
    if (accessorsInfo.isFluent()) {
      return fieldName;
    }

    final boolean useBooleanPrefix = isBoolean && !accessorsInfo.isDoNotUseIsPrefix();

    if (useBooleanPrefix) {
      if (fieldName.startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
        final String baseName = fieldName.substring(2);
        result = buildName(booleanPrefix, baseName, accessorsInfo.getCapitalizationStrategy());
      }
      else {
        result = buildName(booleanPrefix, fieldName, accessorsInfo.getCapitalizationStrategy());
      }
    }
    else {
      result = buildName(normalPrefix, fieldName, accessorsInfo.getCapitalizationStrategy());
    }
    return result;
  }


  /**
   * Returns all names of methods that would represent the getter for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [isRunning, getRunning, isIsRunning, getIsRunning]}
   *
   * @param fieldName     the name of the field.
   * @param isBoolean     if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllGetterNames(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean) {
    return toAllAccessorNames(accessorsInfo, fieldName, isBoolean, "is", "get");
  }

  /**
   * Returns all names of methods that would represent the setter for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [setRunning, setIsRunning]}
   *
   * @param fieldName     the name of the field.
   * @param isBoolean     if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllSetterNames(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean) {
    return toAllAccessorNames(accessorsInfo, fieldName, isBoolean, "set", "set");
  }

  /**
   * Returns all names of methods that would represent the wither for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [withRunning, withIsRunning]}
   *
   * @param fieldName     the name of the field.
   * @param isBoolean     if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllWitherNames(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean) {
    if (accessorsInfo.isFluent()) {
      throw new IllegalArgumentException("@Wither does not support @Accessors(fluent=true)");
    }
    return toAllAccessorNames(accessorsInfo, fieldName, isBoolean, "with", "with");
  }

  private static Collection<String> toAllAccessorNames(AccessorsInfo accessorsInfo,
                                                       String fieldName,
                                                       boolean isBoolean,
                                                       String booleanPrefix,
                                                       String normalPrefix) {
    Collection<String> result = new HashSet<>();

    fieldName = accessorsInfo.removePrefix(fieldName);
    if (accessorsInfo.isFluent()) {
      result.add(StringUtil.decapitalize(fieldName));
      return result;
    }

    final CapitalizationStrategy capitalizationStrategy = accessorsInfo.getCapitalizationStrategy();
    if (isBoolean) {
      result.add(buildName(normalPrefix, fieldName, capitalizationStrategy));
      result.add(buildName(booleanPrefix, fieldName, capitalizationStrategy));

      if (fieldName.startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
        final String baseName = fieldName.substring(2);
        result.add(buildName(normalPrefix, baseName, capitalizationStrategy));
        result.add(buildName(booleanPrefix, baseName, capitalizationStrategy));
      }
    }
    else {
      result.add(buildName(normalPrefix, fieldName, capitalizationStrategy));
    }
    return result;
  }

  public static String buildAccessorName(String prefix, String suffix, CapitalizationStrategy capitalizationStrategy) {
    if (suffix.isEmpty()) {
      return prefix;
    }
    if (prefix.isEmpty()) {
      return suffix;
    }
    return buildName(prefix, suffix, capitalizationStrategy);
  }

  private static String buildName(String prefix, String suffix, CapitalizationStrategy capitalizationStrategy) {
    return prefix + capitalizationStrategy.capitalize(suffix);
  }

  public static String camelCaseToConstant(String fieldName) {
    if (fieldName == null || fieldName.isEmpty()) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    b.append(Character.toUpperCase(fieldName.charAt(0)));
    for (int i = 1; i < fieldName.length(); i++) {
      char c = fieldName.charAt(i);
      if (Character.isUpperCase(c)) {
        b.append('_');
      }
      b.append(Character.toUpperCase(c));
    }
    return b.toString();
  }
}
