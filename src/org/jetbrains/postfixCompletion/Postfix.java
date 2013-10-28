package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.patterns.PlatformPatterns;

public final class Postfix extends CompletionContributor {
    public Postfix() {
        super();

        extend(CompletionType.BASIC,
                PlatformPatterns
                    .psiElement(),

                //PlatformPatterns
                //        .psiElement(JavaElementType.REFERENCE_EXPRESSION)
                //        .withLanguage(JavaLanguage.INSTANCE),

                new CompletionParametersCompletionProvider()
        );
    }

}