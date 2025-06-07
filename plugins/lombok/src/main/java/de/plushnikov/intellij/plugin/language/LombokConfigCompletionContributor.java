package de.plushnikov.intellij.plugin.language;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigPsiUtil;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.lombokconfig.LombokNullAnnotationLibraryDefned;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class LombokConfigCompletionContributor extends CompletionContributor {

  private static final String LOMBOK_EQUALS_AND_HASH_CODE_CALL_SUPER = ConfigKey.EQUALSANDHASHCODE_CALL_SUPER.getConfigKey();
  private static final String LOMBOK_TOSTRING_CALL_SUPER = ConfigKey.TOSTRING_CALL_SUPER.getConfigKey();
  private static final String LOMBOK_ACCESSORS_JAVA_BEANS_SPEC_CAPITALIZATION =
    ConfigKey.ACCESSORS_JAVA_BEANS_SPEC_CAPITALIZATION.getConfigKey();
  private static final String LOMBOK_ADD_NULL_ANNOTATIONS = ConfigKey.ADD_NULL_ANNOTATIONS.getConfigKey();

  public LombokConfigCompletionContributor() {
    final Collection<String> booleanOptions = Set.of(
      ConfigKey.CONFIG_STOP_BUBBLING.getConfigKey(),
      ConfigKey.ADD_GENERATED_ANNOTATION.getConfigKey(),
      ConfigKey.ADD_SUPPRESS_FB_WARNINGS.getConfigKey(),
      ConfigKey.ACCESSORS_CHAIN.getConfigKey(), ConfigKey.ACCESSORS_FLUENT.getConfigKey(),
      ConfigKey.ACCESSORS_MAKE_FINAL.getConfigKey(),
      ConfigKey.ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES.getConfigKey(),
      ConfigKey.ANYCONSTRUCTOR_ADD_CONSTRUCTOR_PROPERTIES.getConfigKey(),
      ConfigKey.EQUALSANDHASHCODE_DO_NOT_USE_GETTERS.getConfigKey(),
      ConfigKey.GETTER_NO_IS_PREFIX.getConfigKey(),
      ConfigKey.LOG_FIELD_IS_STATIC.getConfigKey(),
      ConfigKey.TOSTRING_DO_NOT_USE_GETTERS.getConfigKey(),
      ConfigKey.TOSTRING_INCLUDE_FIELD_NAMES.getConfigKey(),
      ConfigKey.TOSTRING_ONLY_EXPLICITLY_INCLUDED.getConfigKey(),
      ConfigKey.FIELDDEFAULTS_FINAL.getConfigKey(), ConfigKey.FIELDDEFAULTS_PRIVATE.getConfigKey(),
      ConfigKey.NO_ARGS_CONSTRUCTOR_EXTRA_PRIVATE.getConfigKey(),
      ConfigKey.ADD_LOMBOK_GENERATED_ANNOTATION.getConfigKey());

    final Collection<String> flagUsageOptions = Set.of(
      ConfigKey.ACCESSORS_FLAG_USAGE.getConfigKey(),
      ConfigKey.ALL_ARGS_CONSTRUCTOR_FLAG_USAGE.getConfigKey(),
      ConfigKey.ANY_CONSTRUCTOR_FLAG_USAGE.getConfigKey(),
      ConfigKey.BUILDER_FLAG_USAGE.getConfigKey(),
      ConfigKey.SUPER_BUILDER_FLAG_USAGE.getConfigKey(),
      ConfigKey.CLEANUP_FLAG_USAGE.getConfigKey(),
      ConfigKey.DATA_FLAG_USAGE.getConfigKey(),
      ConfigKey.DELEGATE_FLAG_USAGE.getConfigKey(),
      ConfigKey.EQUALS_AND_HASHCODE_FLAG_USAGE.getConfigKey(),
      ConfigKey.EXPERIMENTAL_FLAG_USAGE.getConfigKey(),
      ConfigKey.EXTENSION_METHOD_FLAG_USAGE.getConfigKey(),
      ConfigKey.FIELD_DEFAULTS_FLAG_USAGE.getConfigKey(),
      ConfigKey.FIELD_NAME_CONSTANT_FLAG_USAGE.getConfigKey(),
      ConfigKey.GETTER_FLAG_USAGE.getConfigKey(),
      ConfigKey.GETTER_LAZY_FLAG_USAGE.getConfigKey(),
      ConfigKey.HELPER_FLAG_USAGE.getConfigKey(),
      ConfigKey.JACKSONIZED_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOCKED_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_APACHE_COMMONS_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_CUSTOM_USAGE.getConfigKey(),
      ConfigKey.LOG_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_FLOGGER_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_JAVA_UTIL_LOGGING_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_JBOSSLOG_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_LOG4J_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_LOG4J2_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_SLF4J_FLAG_USAGE.getConfigKey(),
      ConfigKey.LOG_XSLF4J_FLAG_USAGE.getConfigKey(),
      ConfigKey.NO_ARGS_CONSTRUCTOR_FLAG_USAGE.getConfigKey(),
      ConfigKey.NONNULL_FLAG_USAGE.getConfigKey(),
      ConfigKey.ONX_FLAG_USAGE.getConfigKey(),
      ConfigKey.REQUIRED_ARGS_CONSTRUCTOR_FLAG_USAGE.getConfigKey(),
      ConfigKey.SETTER_FLAG_USAGE.getConfigKey(),
      ConfigKey.SNEAKY_THROWS_FLAG_USAGE.getConfigKey(),
      ConfigKey.STANDARD_EXCEPTION_FLAG_USAGE.getConfigKey(),
      ConfigKey.SYNCHRONIZED_FLAG_USAGE.getConfigKey(),
      ConfigKey.TOSTRING_FLAG_USAGE.getConfigKey(),
      ConfigKey.UTILITY_CLASS_FLAG_USAGE.getConfigKey(),
      ConfigKey.VAL_FLAG_USAGE.getConfigKey(),
      ConfigKey.VALUE_FLAG_USAGE.getConfigKey(),
      ConfigKey.VAR_FLAG_USAGE.getConfigKey(),
      ConfigKey.WITH_FLAG_USAGE.getConfigKey(),
      ConfigKey.WITHBY_FLAG_USAGE.getConfigKey());

    final Collection<String> otherOptions = Set.of(
      ConfigKey.ACCESSORS_PREFIX.getConfigKey(), LOMBOK_ACCESSORS_JAVA_BEANS_SPEC_CAPITALIZATION,
      ConfigKey.COPYABLE_ANNOTATIONS.getConfigKey(),
      ConfigKey.LOG_FIELDNAME.getConfigKey(), ConfigKey.LOG_CUSTOM_DECLARATION.getConfigKey(),
      ConfigKey.NONNULL_EXCEPTIONTYPE.getConfigKey(), ConfigKey.EQUALSANDHASHCODE_CALL_SUPER.getConfigKey(),
      ConfigKey.FIELD_NAME_CONSTANTS_PREFIX.getConfigKey(), ConfigKey.FIELD_NAME_CONSTANTS_SUFFIX.getConfigKey(),
      ConfigKey.FIELD_NAME_CONSTANTS_TYPENAME.getConfigKey(), ConfigKey.FIELD_NAME_CONSTANTS_UPPERCASE.getConfigKey(),
      ConfigKey.TOSTRING_CALL_SUPER.getConfigKey(), ConfigKey.BUILDER_CLASS_NAME.getConfigKey(),
      ConfigKey.ADD_NULL_ANNOTATIONS.getConfigKey());

    final Collection<String> allOptions = new HashSet<>(booleanOptions);
    allOptions.addAll(flagUsageOptions);
    allOptions.addAll(otherOptions);

    extend(CompletionType.BASIC,
           PsiJavaPatterns.psiElement(LombokConfigTypes.VALUE).withLanguage(LombokConfigLanguage.INSTANCE),
           new CompletionProvider<>() {
             @Override
             public void addCompletions(@NotNull CompletionParameters parameters,
                                        @NotNull ProcessingContext context,
                                        @NotNull CompletionResultSet resultSet) {
               PsiElement psiElement = parameters.getPosition().getParent();
               if (psiElement instanceof LombokConfigProperty) {
                 final String configPropertyKey = StringUtil.notNullize(LombokConfigPsiUtil.getKey((LombokConfigProperty)psiElement));
                 if (booleanOptions.contains(configPropertyKey)) {
                   resultSet.addElement(LookupElementBuilder.create("true"));
                   resultSet.addElement(LookupElementBuilder.create("false"));
                 }
                 else if (flagUsageOptions.contains(configPropertyKey)) {
                   resultSet.addElement(LookupElementBuilder.create("ALLOW"));
                   resultSet.addElement(LookupElementBuilder.create("WARNING"));
                   resultSet.addElement(LookupElementBuilder.create("ERROR"));
                 }
                 else if (LOMBOK_EQUALS_AND_HASH_CODE_CALL_SUPER.equals(configPropertyKey) ||
                          LOMBOK_TOSTRING_CALL_SUPER.equals(configPropertyKey)) {
                   resultSet.addElement(LookupElementBuilder.create("CALL"));
                   resultSet.addElement(LookupElementBuilder.create("SKIP"));
                   resultSet.addElement(LookupElementBuilder.create("WARN"));
                 }
                 else if (LOMBOK_ACCESSORS_JAVA_BEANS_SPEC_CAPITALIZATION.equals(configPropertyKey)) {
                   resultSet.addElement(LookupElementBuilder.create("BASIC"));
                   resultSet.addElement(LookupElementBuilder.create("BEANSPEC"));
                 }
                 else if (LOMBOK_ADD_NULL_ANNOTATIONS.equals(configPropertyKey)) {
                   for (LombokNullAnnotationLibraryDefned library : LombokNullAnnotationLibraryDefned.values()) {
                     resultSet.addElement(LookupElementBuilder.create(library.getKey()));
                   }
                   resultSet.addElement(LookupElementBuilder.create("CUSTOM:[TYPE_USE:]nonnull.annotation.type:nullable.annotation.type"));
                 }
               }
             }
           }
    );

    extend(CompletionType.BASIC,
           PsiJavaPatterns.psiElement(LombokConfigTypes.KEY).withLanguage(LombokConfigLanguage.INSTANCE),
           new CompletionProvider<>() {
             @Override
             public void addCompletions(@NotNull CompletionParameters parameters,
                                        @NotNull ProcessingContext context,
                                        @NotNull CompletionResultSet resultSet) {
               for (String contribution : allOptions) {
                 resultSet.addElement(LookupElementBuilder.create(contribution));
               }
             }
           }
    );
  }
}
