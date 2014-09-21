package de.plushnikov.intellij.plugin.language;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.string;

public class LombokConfigCompletionContributor extends CompletionContributor {

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
        "lombok.log.xslf4j.flagUsage", "lombok.noArgsConstructor.flagUsage", "lombok.nonNull.flagUsage",
        "lombok.requiredArgsConstructor.flagUsage", "lombok.setter.flagUsage", "lombok.sneakyThrows.flagUsage",
        "lombok.synchronized.flagUsage", "lombok.toString.flagUsage", "lombok.val.flagUsage", "lombok.value.flagUsage",
        "lombok.wither.flagUsage"));

    final Collection<String> otherOptions = new HashSet<String>(Arrays.asList(
        "lombok.accessors.prefix", "lombok.log.fieldName", "lombok.nonNull.exceptionType"));

    final Collection<String> allOptions = new HashSet<String>(booleanOptions);
    allOptions.addAll(flagUsageOptions);
    allOptions.addAll(otherOptions);

    extend(CompletionType.BASIC,
        psiElement(LombokConfigTypes.VALUE).withLanguage(LombokConfigLanguage.INSTANCE),
        new CompletionProvider<CompletionParameters>() {
          public void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet resultSet) {
            PsiElement psiElement = parameters.getPosition().getParent();
            if (psiElement instanceof LombokConfigProperty) {
              final String configPropertyKey = StringUtil.notNullize(((LombokConfigProperty) psiElement).getKey());
              if (booleanOptions.contains(configPropertyKey)) {
                resultSet.addElement(LookupElementBuilder.create("true"));
                resultSet.addElement(LookupElementBuilder.create("false"));
              } else if (flagUsageOptions.contains(configPropertyKey)) {
                resultSet.addElement(LookupElementBuilder.create("WARNING"));
                resultSet.addElement(LookupElementBuilder.create("ERROR"));
              }
            }
          }
        }
    );

    extend(CompletionType.BASIC,
        psiElement(LombokConfigTypes.KEY).withLanguage(LombokConfigLanguage.INSTANCE),
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
