package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ProjectLombok Team
 * @author Plushnikov Michail
 * @see lombok.core.handlers.HandlerUtil from https://projectlombok.org/
 */
public final class LombokUtils {
  public static final String LOMBOK_INTERN_FIELD_MARKER = "$";

  public static final String[] NONNULL_ANNOTATIONS = {
    "android.annotation.NonNull",
    "android.support.annotation.NonNull",
    "android.support.annotation.RecentlyNonNull",
    "androidx.annotation.NonNull",
    "androidx.annotation.RecentlyNonNull",
    "com.android.annotations.NonNull",
    "com.google.firebase.database.annotations.NotNull",
    // Even though it's in a database package, it does mean semantically: "Check if never null at the language level", and not 'db column cannot be null'.
    "com.mongodb.lang.NonNull",
    // Even though mongo is a DB engine, this semantically refers to language, not DB table designs (mongo is a document DB engine, so this isn't surprising perhaps).
    "com.sun.istack.NotNull",
    "com.unboundid.util.NotNull",
    "edu.umd.cs.findbugs.annotations.NonNull",
    "io.micrometer.core.lang.NonNull",
    "io.reactivex.annotations.NonNull",
    "io.reactivex.rxjava3.annotations.NonNull",
    "jakarta.annotation.Nonnull",
    "javax.annotation.Nonnull",
    // "javax.validation.constraints.NotNull", // The field might contain a null value until it is persisted.
    "libcore.util.NonNull",
    "lombok.NonNull",
    "org.checkerframework.checker.nullness.qual.NonNull",
    "org.checkerframework.checker.nullness.compatqual.NonNullDecl",
    "org.checkerframework.checker.nullness.compatqual.NonNullType",
    "org.codehaus.commons.nullanalysis.NotNull",
    "org.eclipse.jdt.annotation.NonNull",
    "org.jetbrains.annotations.NotNull",
    "org.jmlspecs.annotation.NonNull",
    "org.jspecify.annotations.NonNull",
    "org.netbeans.api.annotations.common.NonNull",
    "org.springframework.lang.NonNull",
    "reactor.util.annotation.NonNull",
  };

  static final String[] BASE_COPYABLE_ANNOTATIONS = {
    "android.annotation.NonNull",
    "android.annotation.Nullable",
    "android.support.annotation.NonNull",
    "android.support.annotation.Nullable",
    "android.support.annotation.RecentlyNonNull",
    "android.support.annotation.RecentlyNullable",
    "androidx.annotation.NonNull",
    "androidx.annotation.Nullable",
    "androidx.annotation.RecentlyNonNull",
    "androidx.annotation.RecentlyNullable",
    "com.android.annotations.NonNull",
    "com.android.annotations.Nullable",
    // "com.google.api.server.spi.config.Nullable", - let's think about this one a little, as it is targeted solely at parameters, so you can't even put it on fields. If we choose to support it, we should REMOVE it from the field, then - that's not something we currently support.
    "com.google.firebase.database.annotations.NotNull",
    "com.google.firebase.database.annotations.Nullable",
    "com.mongodb.lang.NonNull",
    "com.mongodb.lang.Nullable",
    "com.sun.istack.NotNull",
    "com.sun.istack.Nullable",
    "com.unboundid.util.NotNull",
    "com.unboundid.util.Nullable",
    "edu.umd.cs.findbugs.annotations.CheckForNull",
    "edu.umd.cs.findbugs.annotations.NonNull",
    "edu.umd.cs.findbugs.annotations.Nullable",
    "edu.umd.cs.findbugs.annotations.PossiblyNull",
    "edu.umd.cs.findbugs.annotations.UnknownNullness",
    "io.micrometer.core.lang.NonNull",
    "io.micrometer.core.lang.Nullable",
    "io.reactivex.annotations.NonNull",
    "io.reactivex.annotations.Nullable",
    "io.reactivex.rxjava3.annotations.NonNull",
    "io.reactivex.rxjava3.annotations.Nullable",
    "jakarta.annotation.Nonnull",
    "jakarta.annotation.Nullable",
    "javax.annotation.CheckForNull",
    "javax.annotation.Nonnull",
    "javax.annotation.Nullable",
    //			"javax.validation.constraints.NotNull", // - this should definitely not be included; validation is not about language-level nullity, therefore should not be in this core list.
    "libcore.util.NonNull",
    "libcore.util.Nullable",
    "lombok.NonNull",
    "org.checkerframework.checker.nullness.compatqual.NonNullDecl",
    "org.checkerframework.checker.nullness.compatqual.NonNullType",
    "org.checkerframework.checker.nullness.compatqual.NullableDecl",
    "org.checkerframework.checker.nullness.compatqual.NullableType",
    "org.checkerframework.checker.nullness.qual.NonNull",
    "org.checkerframework.checker.nullness.qual.Nullable",
    "org.codehaus.commons.nullanalysis.NotNull",
    "org.codehaus.commons.nullanalysis.Nullable",
    "org.eclipse.jdt.annotation.NonNull",
    "org.eclipse.jdt.annotation.Nullable",
    "org.jetbrains.annotations.NotNull",
    "org.jetbrains.annotations.Nullable",
    "org.jetbrains.annotations.UnknownNullability",
    "org.jmlspecs.annotation.NonNull",
    "org.jmlspecs.annotation.Nullable",
    "org.jspecify.annotations.Nullable",
    "org.jspecify.annotations.NonNull",
    "org.netbeans.api.annotations.common.CheckForNull",
    "org.netbeans.api.annotations.common.NonNull",
    "org.netbeans.api.annotations.common.NullAllowed",
    "org.netbeans.api.annotations.common.NullUnknown",
    "org.springframework.lang.NonNull",
    "org.springframework.lang.Nullable",
    "reactor.util.annotation.NonNull",
    "reactor.util.annotation.Nullable",

    // Checker Framework annotations.
    // To update Checker Framework annotations, run:
    // grep --recursive --files-with-matches -e '^@Target\b.*TYPE_USE' $CHECKERFRAMEWORK/checker/src/main/java $CHECKERFRAMEWORK/checker-qual/src/main/java $CHECKERFRAMEWORK/checker-util/src/main/java $CHECKERFRAMEWORK/framework/src/main/java | grep '\.java$' | sed 's/.*\/java\//\t\t\t"/' | sed 's/\.java$/",/' | sed 's/\//./g' | sort
    // Only add new annotations, do not remove annotations that have been removed from the latest version of the Checker Framework.
    "org.checkerframework.checker.builder.qual.CalledMethods",
    "org.checkerframework.checker.builder.qual.NotCalledMethods",
    "org.checkerframework.checker.calledmethods.qual.CalledMethods",
    "org.checkerframework.checker.calledmethods.qual.CalledMethodsBottom",
    "org.checkerframework.checker.calledmethods.qual.CalledMethodsPredicate",
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
    "org.checkerframework.checker.formatter.qual.UnknownFormat",
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
    "org.checkerframework.checker.index.qual.UpperBoundLiteral",
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
    "org.checkerframework.checker.lock.qual.NewObject",
    "org.checkerframework.checker.mustcall.qual.MustCall",
    "org.checkerframework.checker.mustcall.qual.MustCallAlias",
    "org.checkerframework.checker.mustcall.qual.MustCallUnknown",
    "org.checkerframework.checker.mustcall.qual.PolyMustCall",
    "org.checkerframework.checker.nullness.qual.KeyFor",
    "org.checkerframework.checker.nullness.qual.KeyForBottom",
    "org.checkerframework.checker.nullness.qual.MonotonicNonNull",
    "org.checkerframework.checker.nullness.qual.NonNull",
    "org.checkerframework.checker.nullness.qual.Nullable",
    "org.checkerframework.checker.nullness.qual.PolyKeyFor",
    "org.checkerframework.checker.nullness.qual.PolyNull",
    "org.checkerframework.checker.nullness.qual.UnknownKeyFor",
    "org.checkerframework.checker.optional.qual.MaybePresent",
    "org.checkerframework.checker.optional.qual.OptionalBottom",
    "org.checkerframework.checker.optional.qual.PolyPresent",
    "org.checkerframework.checker.optional.qual.Present",
    "org.checkerframework.checker.propkey.qual.PropertyKey",
    "org.checkerframework.checker.propkey.qual.PropertyKeyBottom",
    "org.checkerframework.checker.propkey.qual.UnknownPropertyKey",
    "org.checkerframework.checker.regex.qual.PolyRegex",
    "org.checkerframework.checker.regex.qual.Regex",
    "org.checkerframework.checker.regex.qual.RegexBottom",
    "org.checkerframework.checker.regex.qual.UnknownRegex",
    "org.checkerframework.checker.signature.qual.ArrayWithoutPackage",
    "org.checkerframework.checker.signature.qual.BinaryName",
    "org.checkerframework.checker.signature.qual.BinaryNameOrPrimitiveType",
    "org.checkerframework.checker.signature.qual.BinaryNameWithoutPackage",
    "org.checkerframework.checker.signature.qual.CanonicalName",
    "org.checkerframework.checker.signature.qual.CanonicalNameAndBinaryName",
    "org.checkerframework.checker.signature.qual.CanonicalNameOrEmpty",
    "org.checkerframework.checker.signature.qual.CanonicalNameOrPrimitiveType",
    "org.checkerframework.checker.signature.qual.ClassGetName",
    "org.checkerframework.checker.signature.qual.ClassGetSimpleName",
    "org.checkerframework.checker.signature.qual.DotSeparatedIdentifiers",
    "org.checkerframework.checker.signature.qual.DotSeparatedIdentifiersOrPrimitiveType",
    "org.checkerframework.checker.signature.qual.FieldDescriptor",
    "org.checkerframework.checker.signature.qual.FieldDescriptorForPrimitive",
    "org.checkerframework.checker.signature.qual.FieldDescriptorWithoutPackage",
    "org.checkerframework.checker.signature.qual.FqBinaryName",
    "org.checkerframework.checker.signature.qual.FullyQualifiedName",
    "org.checkerframework.checker.signature.qual.Identifier",
    "org.checkerframework.checker.signature.qual.IdentifierOrPrimitiveType",
    "org.checkerframework.checker.signature.qual.InternalForm",
    "org.checkerframework.checker.signature.qual.MethodDescriptor",
    "org.checkerframework.checker.signature.qual.PolySignature",
    "org.checkerframework.checker.signature.qual.PrimitiveType",
    "org.checkerframework.checker.signature.qual.SignatureBottom",
    "org.checkerframework.checker.signedness.qual.PolySigned",
    "org.checkerframework.checker.signedness.qual.Signed",
    "org.checkerframework.checker.signedness.qual.SignednessBottom",
    "org.checkerframework.checker.signedness.qual.SignednessGlb",
    "org.checkerframework.checker.signedness.qual.SignedPositive",
    "org.checkerframework.checker.signedness.qual.SignedPositiveFromUnsigned",
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
    "org.checkerframework.checker.units.qual.Force",
    "org.checkerframework.checker.units.qual.g",
    "org.checkerframework.checker.units.qual.h",
    "org.checkerframework.checker.units.qual.K",
    "org.checkerframework.checker.units.qual.kg",
    "org.checkerframework.checker.units.qual.km",
    "org.checkerframework.checker.units.qual.km2",
    "org.checkerframework.checker.units.qual.km3",
    "org.checkerframework.checker.units.qual.kmPERh",
    "org.checkerframework.checker.units.qual.kN",
    "org.checkerframework.checker.units.qual.Length",
    "org.checkerframework.checker.units.qual.Luminance",
    "org.checkerframework.checker.units.qual.m",
    "org.checkerframework.checker.units.qual.m2",
    "org.checkerframework.checker.units.qual.m3",
    "org.checkerframework.checker.units.qual.Mass",
    "org.checkerframework.checker.units.qual.min",
    "org.checkerframework.checker.units.qual.mm",
    "org.checkerframework.checker.units.qual.mm2",
    "org.checkerframework.checker.units.qual.mm3",
    "org.checkerframework.checker.units.qual.mol",
    "org.checkerframework.checker.units.qual.mPERs",
    "org.checkerframework.checker.units.qual.mPERs2",
    "org.checkerframework.checker.units.qual.N",
    "org.checkerframework.checker.units.qual.PolyUnit",
    "org.checkerframework.checker.units.qual.radians",
    "org.checkerframework.checker.units.qual.s",
    "org.checkerframework.checker.units.qual.Speed",
    "org.checkerframework.checker.units.qual.Substance",
    "org.checkerframework.checker.units.qual.t",
    "org.checkerframework.checker.units.qual.Temperature",
    "org.checkerframework.checker.units.qual.Time",
    "org.checkerframework.checker.units.qual.UnitsBottom",
    "org.checkerframework.checker.units.qual.UnknownUnits",
    "org.checkerframework.checker.units.qual.Volume",
    "org.checkerframework.common.aliasing.qual.LeakedToResult",
    "org.checkerframework.common.aliasing.qual.MaybeAliased",
    "org.checkerframework.common.aliasing.qual.NonLeaked",
    "org.checkerframework.common.aliasing.qual.Unique",
    "org.checkerframework.common.initializedfields.qual.InitializedFields",
    "org.checkerframework.common.initializedfields.qual.InitializedFieldsBottom",
    "org.checkerframework.common.initializedfields.qual.PolyInitializedFields",
    "org.checkerframework.common.reflection.qual.ClassBound",
    "org.checkerframework.common.reflection.qual.ClassVal",
    "org.checkerframework.common.reflection.qual.ClassValBottom",
    "org.checkerframework.common.reflection.qual.MethodVal",
    "org.checkerframework.common.reflection.qual.MethodValBottom",
    "org.checkerframework.common.reflection.qual.UnknownClass",
    "org.checkerframework.common.reflection.qual.UnknownMethod",
    "org.checkerframework.common.returnsreceiver.qual.BottomThis",
    "org.checkerframework.common.returnsreceiver.qual.This",
    "org.checkerframework.common.returnsreceiver.qual.UnknownThis",
    "org.checkerframework.common.subtyping.qual.Bottom",
    "org.checkerframework.common.util.report.qual.ReportUnqualified",
    "org.checkerframework.common.value.qual.ArrayLen",
    "org.checkerframework.common.value.qual.ArrayLenRange",
    "org.checkerframework.common.value.qual.BoolVal",
    "org.checkerframework.common.value.qual.BottomVal",
    "org.checkerframework.common.value.qual.DoubleVal",
    "org.checkerframework.common.value.qual.EnumVal",
    "org.checkerframework.common.value.qual.IntRange",
    "org.checkerframework.common.value.qual.IntVal",
    "org.checkerframework.common.value.qual.MatchesRegex",
    "org.checkerframework.common.value.qual.MinLen",
    "org.checkerframework.common.value.qual.PolyValue",
    "org.checkerframework.common.value.qual.StringVal",
    "org.checkerframework.common.value.qual.UnknownVal",
    "org.checkerframework.framework.qual.PurityUnqualified",
  };

  // The following two lists contain all annotations that can be copied from the field to the getter or setter.
  // Right now, it only contains Jackson annotations.
  // Jackson's annotation processing roughly works as follows: To calculate the annotation for a JSON property, Jackson
  // builds a triple of the Java field and the corresponding setter and getter methods. It is sufficient for an annotation
  // to be present on one of those to become effective. E.g., a @JsonIgnore on a setter completely ignores the JSON property,
  // not only during deserialization, but also when serializing. Therefore, in most cases it is _not_ necessary to copy the
  // annotations. It may even harm, as Jackson considers some annotations inheritable, and this "virtual inheritance" only
  // affects annotations on setter/getter, but not on private fields.
  // However, there are two exceptions where we have to copy the annotations:
  // 1. When using a builder to deserialize, Jackson does _not_ "propagate" the annotations to the setter methods of the
  //    builder, i.e. annotations like @JsonIgnore on the field will not be respected when deserializing with a builder.
  //    Thus, those annotations should be copied to the builder's setters.
  // 2. If the getter/setter methods do not follow the exact beanspec naming strategy, Jackson will not correctly detect the
  //    field-getter-setter triple, and annotations may not work as intended.
  //    However, we cannot always know what the user's intention is. Thus, lombok should only fix those cases where it is
  //    obvious what the user wants. That is the case for a `@Jacksonized @Accessors(fluent=true)`.
  static final String[] COPY_TO_GETTER_ANNOTATIONS = {
    "com.fasterxml.jackson.annotation.JsonFormat",
      "com.fasterxml.jackson.annotation.JsonIgnore",
      "com.fasterxml.jackson.annotation.JsonIgnoreProperties",
      "com.fasterxml.jackson.annotation.JsonProperty",
      "com.fasterxml.jackson.annotation.JsonSubTypes",
      "com.fasterxml.jackson.annotation.JsonTypeInfo",
      "com.fasterxml.jackson.annotation.JsonUnwrapped",
      "com.fasterxml.jackson.annotation.JsonView",
      "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper",
      "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty",
      "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText",
  };
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
    "com.fasterxml.jackson.annotation.JsonUnwrapped",
    "com.fasterxml.jackson.annotation.JsonView",
    "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
    "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper",
    "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty",
    "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText",
  };

  static final String[] COPY_TO_BUILDER_SINGULAR_SETTER_ANNOTATIONS = {
    "com.fasterxml.jackson.annotation.JsonAnySetter"};

  // In order to let Jackson recognize certain configuration annotations when deserializing using a builder, those must
  // be copied to the generated builder class.
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
    "com.fasterxml.jackson.databind.annotation.JsonNaming",
  };

  public static String getGetterName(final @NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    return getGetterName(psiField, accessorsInfo);
  }

  public static String getGetterName(@NotNull PsiField psiField, @NotNull AccessorsInfo accessorsInfo) {
    final String psiFieldName = psiField.getName();
    final boolean isBoolean = PsiTypes.booleanType().equals(psiField.getType());

    return toGetterName(accessorsInfo, psiFieldName, isBoolean);
  }

  public static String getWithByName(@NotNull PsiVariable psiVariable, @NotNull AccessorsInfo accessorsInfo) {
    final String psiFieldName = psiVariable.getName();
    final boolean isBoolean = PsiTypes.booleanType().equals(psiVariable.getType());

    return toWithByName(accessorsInfo, psiFieldName, isBoolean);
  }

  public static String getSetterName(@NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
    return getSetterName(psiField, accessorsInfo);
  }

  public static String getSetterName(@NotNull PsiField psiField, @NotNull AccessorsInfo accessorsInfo) {
    return toSetterName(accessorsInfo, psiField.getName(), PsiTypes.booleanType().equals(psiField.getType()));
  }

  public static String getWitherName(@NotNull PsiVariable psiVariable, @NotNull AccessorsInfo accessorsInfo) {
    return getWitherName(psiVariable, psiVariable.getName(), accessorsInfo);
  }

  public static String getWitherName(@NotNull PsiVariable psiVariable, @Nullable String variableName, @NotNull AccessorsInfo accessorsInfo) {
    return toWitherName(accessorsInfo.withFluent(false), variableName, PsiTypes.booleanType().equals(psiVariable.getType()));
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
  public static String toGetterName(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
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
  public static String toSetterName(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
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
  public static String toWitherName(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAccessorName(accessors.withFluent(false), fieldName, isBoolean, "with", "with");
  }

  /**
   * Generates a withBy name from a given field name.
   *
   * Strategy: The same as the {@code toWithName} strategy, but then append {@code "By"} at the end.
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type {@code java.lang.Boolean}, you should provide {@code false}.
   * @return The with name for this field, or {@code null} if this field does not fit expected patterns and therefore cannot be turned into a getter name.
   */
  public static String toWithByName(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAccessorName(accessors.withFluent(false), fieldName, isBoolean, "with", "with") + "By";
  }

  private static String toAccessorName(@NotNull AccessorsInfo accessorsInfo,
                                       String fieldName,
                                       boolean isBoolean,
                                       String booleanPrefix,
                                       String normalPrefix) {
    final String result;

    fieldName = accessorsInfo.removePrefix(fieldName);
    if (fieldName == null) return "";

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
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllGetterNames(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAllAccessorNames(accessors, fieldName, isBoolean, "is", "get");
  }

  /**
   * Returns all names of methods that would represent the setter for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [setRunning, setIsRunning]}
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllSetterNames(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAllAccessorNames(accessors, fieldName, isBoolean, "set", "set");
  }

  /**
   * Returns all names of methods that would represent the wither for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [withRunning, withIsRunning]}
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllWitherNames(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAllAccessorNames(accessors.withFluent(false), fieldName, isBoolean, "with", "with");
  }

  /**
   * Returns all names of methods that would represent the withBy for a field with the provided name.
   *
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [withRunningBy, withIsRunningBy]}
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static List<String> toAllWithByNames(@NotNull AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    List<String> result = new ArrayList<>(toAllAccessorNames(accessors.withFluent(false), fieldName, isBoolean, "with", "with"));
    result.replaceAll(s -> s + "By");
    return result;
  }

  private static Collection<String> toAllAccessorNames(@NotNull AccessorsInfo accessorsInfo,
                                                       String fieldName,
                                                       boolean isBoolean,
                                                       String booleanPrefix,
                                                       String normalPrefix) {
    Collection<String> result = new HashSet<>();

    fieldName = accessorsInfo.removePrefix(fieldName);
    if (fieldName == null) return Collections.emptyList();

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
    CapitalizationStrategy strategy = null == capitalizationStrategy ? CapitalizationStrategy.defaultValue() : capitalizationStrategy;
    return prefix + strategy.capitalize(suffix);
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
