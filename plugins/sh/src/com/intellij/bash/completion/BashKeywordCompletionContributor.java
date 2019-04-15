package com.intellij.bash.completion;

import com.intellij.bash.psi.BashFile;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.StandardPatterns;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class BashKeywordCompletionContributor extends CompletionContributor implements DumbAware {

  public BashKeywordCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inFile(StandardPatterns.instanceOf(BashFile.class)),
        new BashKeywordCompletionProvider("if", "elif", "select", "case", "for", "while", "until", "function"));
  }
}
