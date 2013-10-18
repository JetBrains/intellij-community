package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class PyParameterCompletionContributor extends CompletionContributor {
  public PyParameterCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().inside(PyParameterList.class).afterLeaf("*"),
           new ParameterCompletionProvider("args"));
    extend(CompletionType.BASIC,
           psiElement().inside(PyParameterList.class).afterLeaf("**"),
           new ParameterCompletionProvider("kwargs"));
  }

  private static class ParameterCompletionProvider extends CompletionProvider<CompletionParameters> {
    private String myName;

    private ParameterCompletionProvider(String name) {
      myName = name;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create(myName).withIcon(AllIcons.Nodes.Parameter));
    }
  }
}
