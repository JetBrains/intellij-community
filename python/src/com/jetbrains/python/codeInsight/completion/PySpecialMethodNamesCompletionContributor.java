/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Completes predefined method names like __str__
 * User: dcheryasov
 * Date: Dec 3, 2009 10:06:12 AM
 */
public class PySpecialMethodNamesCompletionContributor extends CompletionContributor {
  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(@NotNull AutoCompletionContext context) {
    // auto-insert the obvious only case; else show other cases. 
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  public PySpecialMethodNamesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(psiElement().inside(psiElement(PyFunction.class).inside(psiElement(PyClass.class))))
        .and(psiElement().afterLeaf("def"))
     ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          LanguageLevel languageLevel = LanguageLevel.forElement(parameters.getOriginalFile());
          for (Map.Entry<String, PyNames.BuiltinDescription> entry: PyNames.getBuiltinMethods(languageLevel).entrySet()) {
            LookupElementBuilder item = LookupElementBuilder
              .create(entry.getKey() + entry.getValue().getSignature())
              .bold()
              .withTypeText("predefined")
              .withIcon(PythonIcons.Python.Nodes.Cyan_dot)
            ;
            result.addElement(TailTypeDecorator.withTail(item, TailType.CASE_COLON));
          }
        }
      }
    );
  }
}
