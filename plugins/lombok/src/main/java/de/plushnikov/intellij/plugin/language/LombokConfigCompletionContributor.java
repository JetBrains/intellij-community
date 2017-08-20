package de.plushnikov.intellij.plugin.language;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigPsiUtil;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class LombokConfigCompletionContributor extends CompletionContributor {

  private static final String LOMBOK_EQUALS_AND_HASH_CODE_CALL_SUPER = ConfigKey.EQUALSANDHASHCODE_CALL_SUPER.getConfigKey();

  public LombokConfigCompletionContributor() {
    final Collection<String> booleanOptions = new HashSet<String>(Arrays.asList(
      "config.stopBubbling", "lombok.accessors.chain", "lombok.accessors.fluent",
      "lombok.anyConstructor.suppressConstructorProperties", "lombok.equalsAndHashCode.doNotUseGetters", "lombok.getter.noIsPrefix",
      "lombok.log.fieldIsStatic", "lombok.toString.doNotUseGetters", "lombok.toString.includeFieldNames"));

    final Collection<String> flagUsageOptions = new HashSet<String>(Arrays.asList(
      "lombok.accessors.flagUsage", "lombok.allArgsConstructor.flagUsage", "lombok.anyConstructor.flagUsage",
      "lombok.builder.flagUsage", "lombok.cleanup.flagUsage", "lombok.data.flagUsage", "lombok.delegate.flagUsage",
      "lombok.equalsAndHashCode.flagUsage", "lombok.experimental.flagUsage", "lombok.extensionMethod.flagUsage",
      "lombok.fieldDefaults.flagUsage", "lombok.getter.flagUsage", "lombok.getter.lazy.flagUsage",
      "lombok.log.apacheCommons.flagUsage", "lombok.log.flagUsage", "lombok.log.javaUtilLogging.flagUsage",
      "lombok.log.log4j.flagUsage", "lombok.log.log4j2.flagUsage", "lombok.log.slf4j.flagUsage",
      "lombok.log.xslf4j.flagUsage", "lombok.log.jbosslog.flagUsage",
      "lombok.noArgsConstructor.flagUsage", "lombok.nonNull.flagUsage",
      "lombok.requiredArgsConstructor.flagUsage", "lombok.setter.flagUsage", "lombok.sneakyThrows.flagUsage",
      "lombok.synchronized.flagUsage", "lombok.toString.flagUsage", "lombok.val.flagUsage", "lombok.value.flagUsage",
      "lombok.wither.flagUsage"));

    final Collection<String> flagUsageAllowable = new HashSet<String>(Collections.singletonList(
      "lombok.var.flagUsage"
    ));

    final Collection<String> otherOptions = new HashSet<String>(Arrays.asList(
      ConfigKey.ACCESSORS_PREFIX.getConfigKey(), ConfigKey.LOG_FIELDNAME.getConfigKey(),
      ConfigKey.NONNULL_EXCEPTIONTYPE.getConfigKey(), ConfigKey.EQUALSANDHASHCODE_CALL_SUPER.getConfigKey()));

    final Collection<String> allOptions = new HashSet<String>(booleanOptions);
    allOptions.addAll(flagUsageOptions);
    allOptions.addAll(flagUsageAllowable);
    allOptions.addAll(otherOptions);

    extend(CompletionType.BASIC,
      PsiJavaPatterns.psiElement(LombokConfigTypes.VALUE).withLanguage(LombokConfigLanguage.INSTANCE),
      new CompletionProvider<CompletionParameters>() {
        public void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet resultSet) {
          PsiElement psiElement = parameters.getPosition().getParent();
          if (psiElement instanceof LombokConfigProperty) {
            final String configPropertyKey = StringUtil.notNullize(LombokConfigPsiUtil.getKey((LombokConfigProperty) psiElement));
            if (booleanOptions.contains(configPropertyKey)) {
              resultSet.addElement(LookupElementBuilder.create("true"));
              resultSet.addElement(LookupElementBuilder.create("false"));
            } else if (flagUsageOptions.contains(configPropertyKey)) {
              resultSet.addElement(LookupElementBuilder.create("WARNING"));
              resultSet.addElement(LookupElementBuilder.create("ERROR"));
            } else if (flagUsageAllowable.contains(configPropertyKey)) {
              resultSet.addElement(LookupElementBuilder.create("ALLOW"));
              resultSet.addElement(LookupElementBuilder.create("WARNING"));
            } else if (LOMBOK_EQUALS_AND_HASH_CODE_CALL_SUPER.equals(configPropertyKey)) {
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
      new CompletionProvider<CompletionParameters>() {
        public void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet resultSet) {
          for (String contribution : allOptions) {
            resultSet.addElement(LookupElementBuilder.create(contribution));
          }
        }
      }
    );
  }
}
