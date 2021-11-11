package de.plushnikov.intellij.plugin.language;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigPsiUtil;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

public class LombokConfigCompletionContributor extends CompletionContributor {

  private static final String LOMBOK_EQUALS_AND_HASH_CODE_CALL_SUPER = ConfigKey.EQUALSANDHASHCODE_CALL_SUPER.getConfigKey();
  private static final String LOMBOK_TOSTRING_CALL_SUPER = ConfigKey.TOSTRING_CALL_SUPER.getConfigKey();

  public LombokConfigCompletionContributor() {
    final Collection<String> booleanOptions = ContainerUtil.set(
      ConfigKey.CONFIG_STOP_BUBBLING.getConfigKey(),
      ConfigKey.ACCESSORS_CHAIN.getConfigKey(), ConfigKey.ACCESSORS_FLUENT.getConfigKey(),
      ConfigKey.ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES.getConfigKey(),
      ConfigKey.ANYCONSTRUCTOR_ADD_CONSTRUCTOR_PROPERTIES.getConfigKey(),
      ConfigKey.STANDARD_EXCEPTION_ADD_CONSTRUCTOR_PROPERTIES.getConfigKey(),
      ConfigKey.EQUALSANDHASHCODE_DO_NOT_USE_GETTERS.getConfigKey(),
      ConfigKey.GETTER_NO_IS_PREFIX.getConfigKey(),
      ConfigKey.LOG_FIELD_IS_STATIC.getConfigKey(),
      ConfigKey.TOSTRING_DO_NOT_USE_GETTERS.getConfigKey(),
      ConfigKey.TOSTRING_INCLUDE_FIELD_NAMES.getConfigKey(),
      ConfigKey.FIELDDEFAULTS_FINAL.getConfigKey(), ConfigKey.FIELDDEFAULTS_PRIVATE.getConfigKey(),
      ConfigKey.NO_ARGS_CONSTRUCTOR_EXTRA_PRIVATE.getConfigKey());

    final Collection<String> flagUsageOptions = ContainerUtil.set(
      "lombok.accessors.flagUsage", "lombok.allArgsConstructor.flagUsage", "lombok.anyConstructor.flagUsage",
      "lombok.builder.flagUsage", "lombok.cleanup.flagUsage", "lombok.data.flagUsage", "lombok.delegate.flagUsage",
      "lombok.equalsAndHashCode.flagUsage", "lombok.experimental.flagUsage", "lombok.extensionMethod.flagUsage",
      "lombok.fieldDefaults.flagUsage", "lombok.getter.flagUsage", "lombok.getter.lazy.flagUsage",
      "lombok.log.apacheCommons.flagUsage", "lombok.log.flagUsage", "lombok.log.javaUtilLogging.flagUsage",
      "lombok.log.log4j.flagUsage", "lombok.log.log4j2.flagUsage", "lombok.log.slf4j.flagUsage",
      "lombok.log.xslf4j.flagUsage", "lombok.log.jbosslog.flagUsage", "lombok.log.flogger.flagUsage",
      "lombok.noArgsConstructor.flagUsage", "lombok.nonNull.flagUsage",
      "lombok.requiredArgsConstructor.flagUsage", "lombok.setter.flagUsage", "lombok.sneakyThrows.flagUsage",
      "lombok.synchronized.flagUsage", "lombok.toString.flagUsage", "lombok.val.flagUsage", "lombok.value.flagUsage",
      "lombok.wither.flagUsage");

    final Collection<String> flagUsageAllowable = ContainerUtil.set("lombok.var.flagUsage");

    final Collection<String> otherOptions = ContainerUtil.set(
      ConfigKey.ACCESSORS_PREFIX.getConfigKey(), ConfigKey.COPYABLE_ANNOTATIONS.getConfigKey(),
      ConfigKey.LOG_FIELDNAME.getConfigKey(), ConfigKey.LOG_CUSTOM_DECLARATION.getConfigKey(),
      ConfigKey.NONNULL_EXCEPTIONTYPE.getConfigKey(), ConfigKey.EQUALSANDHASHCODE_CALL_SUPER.getConfigKey(),
      ConfigKey.FIELD_NAME_CONSTANTS_PREFIX.getConfigKey(), ConfigKey.FIELD_NAME_CONSTANTS_SUFFIX.getConfigKey(),
      ConfigKey.FIELD_NAME_CONSTANTS_TYPENAME.getConfigKey(), ConfigKey.FIELD_NAME_CONSTANTS_UPPERCASE.getConfigKey(),
      ConfigKey.TOSTRING_CALL_SUPER.getConfigKey(), ConfigKey.BUILDER_CLASS_NAME.getConfigKey());

    final Collection<String> allOptions = new HashSet<>(booleanOptions);
    allOptions.addAll(flagUsageOptions);
    allOptions.addAll(flagUsageAllowable);
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
                   resultSet.addElement(LookupElementBuilder.create("WARNING"));
                   resultSet.addElement(LookupElementBuilder.create("ERROR"));
                 }
                 else if (flagUsageAllowable.contains(configPropertyKey)) {
                   resultSet.addElement(LookupElementBuilder.create("ALLOW"));
                   resultSet.addElement(LookupElementBuilder.create("WARNING"));
                 }
                 else if (LOMBOK_EQUALS_AND_HASH_CODE_CALL_SUPER.equals(configPropertyKey) ||
                          LOMBOK_TOSTRING_CALL_SUPER.equals(configPropertyKey)) {
                   resultSet.addElement(LookupElementBuilder.create("CALL"));
                   resultSet.addElement(LookupElementBuilder.create("SKIP"));
                   resultSet.addElement(LookupElementBuilder.create("WARN"));
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
