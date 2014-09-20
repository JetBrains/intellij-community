package de.plushnikov.intellij.plugin.language;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class LombokConfigCompletionContributor extends CompletionContributor {

  public LombokConfigCompletionContributor() {
    final Collection<String> contributions = new HashSet<String>(Arrays.asList(
        "config.stopBubbling",
        "lombok.accessors.chain",
        "lombok.accessors.flagUsage",
        "lombok.accessors.fluent",
        "lombok.accessors.prefix",
        "lombok.allArgsConstructor.flagUsage",
        "lombok.anyConstructor.flagUsage",
        "lombok.anyConstructor.suppressConstructorProperties",
        "lombok.builder.flagUsage",
        "lombok.cleanup.flagUsage",
        "lombok.data.flagUsage",
        "lombok.delegate.flagUsage",
        "lombok.equalsAndHashCode.doNotUseGetters",
        "lombok.equalsAndHashCode.flagUsage",
        "lombok.experimental.flagUsage",
        "lombok.extensionMethod.flagUsage",
        "lombok.fieldDefaults.flagUsage",
        "lombok.getter.flagUsage",
        "lombok.getter.lazy.flagUsage",
        "lombok.getter.noIsPrefix",
        "lombok.log.apacheCommons.flagUsage",
        "lombok.log.fieldIsStatic",
        "lombok.log.fieldName",
        "lombok.log.flagUsage",
        "lombok.log.javaUtilLogging.flagUsage",
        "lombok.log.log4j.flagUsage",
        "lombok.log.log4j2.flagUsage",
        "lombok.log.slf4j.flagUsage",
        "lombok.log.xslf4j.flagUsage",
        "lombok.noArgsConstructor.flagUsage",
        "lombok.nonNull.exceptionType",
        "lombok.nonNull.flagUsage",
        "lombok.requiredArgsConstructor.flagUsage",
        "lombok.setter.flagUsage",
        "lombok.sneakyThrows.flagUsage",
        "lombok.synchronized.flagUsage",
        "lombok.toString.doNotUseGetters",
        "lombok.toString.flagUsage",
        "lombok.toString.includeFieldNames",
        "lombok.val.flagUsage",
        "lombok.value.flagUsage",
        "lombok.wither.flagUsage"
    ));


    extend(CompletionType.BASIC,
        PlatformPatterns.psiElement(LombokConfigTypes.VALUE).withLanguage(LombokConfigLanguage.INSTANCE),
        new CompletionProvider<CompletionParameters>() {
          public void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet resultSet) {
            resultSet.addElement(LookupElementBuilder.create("true"));
            resultSet.addElement(LookupElementBuilder.create("false"));
            resultSet.addElement(LookupElementBuilder.create("WARNING"));
            resultSet.addElement(LookupElementBuilder.create("ERROR"));
          }
        }
    );
    extend(CompletionType.BASIC,
        PlatformPatterns.psiElement(LombokConfigTypes.KEY).withLanguage(LombokConfigLanguage.INSTANCE),
        new CompletionProvider<CompletionParameters>() {
          public void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet resultSet) {
            resultSet.addElement(LookupElementBuilder.create("clean "));
            for (String contribution : contributions) {
              resultSet.addElement(LookupElementBuilder.create(contribution));
            }
          }
        }
    );
  }
} 
