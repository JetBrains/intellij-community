package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyIcons;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Completes predefined method names like __str__
 * User: dcheryasov
 * Date: Dec 3, 2009 10:06:12 AM
 */
public class PySpecialMethodNamesCompletionContributor extends PySeeingOriginalCompletionContributor {
  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    // auto-insert the obvious only case; else show other cases. 
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }


  private static final FilterPattern IN_METHOD_DEF = new FilterPattern(
    new InSequenceFilter(psiElement(PyFunction.class), psiElement(PyStatementList.class), psiElement(PyClass.class))
  );

  public PySpecialMethodNamesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(IN_METHOD_DEF)
        .and(psiElement().afterLeaf("def"))
     ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          for (Map.Entry<String, PyNames.BuiltinDescription> entry: PyNames.BuiltinMethods.entrySet()) {
            LookupElementBuilder item;
            item = LookupElementBuilder
              .create(entry.getKey() + entry.getValue().getSignature())
              .setBold()
              .setTypeText("predefined")
              .setIcon(PyIcons.PREDEFINED)
            ;
            result.addElement(TailTypeDecorator.withTail(item, TailType.CASE_COLON));
          }
        }
      }
    );
  }
}
