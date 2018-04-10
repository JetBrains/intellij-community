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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyMetaClassCompletionContributor extends CompletionContributor {
  public PyMetaClassCompletionContributor() {
    extend(CompletionType.BASIC,
           PlatformPatterns
             .psiElement()
             .withLanguage(PythonLanguage.getInstance())
             .withParents(PyReferenceExpression.class, PyExpressionStatement.class, PyStatementList.class, PyClass.class)
             .and(hasLanguageLevel(LanguageLevel::isPython2)),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               result.addElement(LookupElementBuilder.create("__metaclass__ = "));
             }
           });
    extend(CompletionType.BASIC,
           PlatformPatterns
            .psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .withParents(PyReferenceExpression.class, PyArgumentList.class, PyClass.class)
            .and(hasLanguageLevel(level -> !level.isPython2())),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               result.addElement(LookupElementBuilder.create("metaclass="));
             }
           });
  }

  public static FilterPattern hasLanguageLevel(@NotNull final Processor<LanguageLevel> processor) {
    return new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof PsiElement) {
          return processor.process(LanguageLevel.forElement((PsiElement)element));
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    });
  }
}
