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
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class PySuperClassAttributesCompletionContributor extends CompletionContributor implements DumbAware {
  public PySuperClassAttributesCompletionContributor() {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement()
             .withParents(PyReferenceExpression.class, PyExpressionStatement.class, PyStatementList.class, PyClass.class),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getOriginalPosition();
               PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);

               if (containingClass == null) {
                 return;
               }
               for (PyTargetExpression expr : getSuperClassAttributes(containingClass)) {
                 result.addElement(LookupElementBuilder.createWithSmartPointer(expr.getName() + " = ", expr));
               }
             }
           }
    );
  }

  public static List<PyTargetExpression> getSuperClassAttributes(@NotNull PyClass cls) {
    List<PyTargetExpression> attrs = new ArrayList<>();
    List<String> seenNames = new ArrayList<>();
    for (PyTargetExpression expr : cls.getClassAttributes()) {
      seenNames.add(expr.getName());
    }
    for (PyClass ancestor : cls.getAncestorClasses(null)) {
      for (PyTargetExpression expr : ancestor.getClassAttributes()) {
        if (!seenNames.contains(expr.getName())) {
          seenNames.add(expr.getName());
          attrs.add(expr);
        }
      }
    }
    return attrs;
  }
}
