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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;


public class PySuperMethodCompletionContributor extends CompletionContributor implements DumbAware {
  public PySuperMethodCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().afterLeafSkipping(psiElement().whitespace(), psiElement().withElementType(PyTokenTypes.DEF_KEYWORD)),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getOriginalPosition();
               PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               PsiElement nextElement = position != null ? position.getNextSibling() : null;
               if (containingClass == null && position instanceof PsiWhiteSpace) {
                 position = PsiTreeUtil.prevLeaf(position);
                 containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               }
               if (containingClass == null) {
                 return;
               }
               Set<String> seenNames = new HashSet<>();
               for (PyFunction function : containingClass.getMethods()) {
                 seenNames.add(function.getName());
               }
               LanguageLevel languageLevel = LanguageLevel.forElement(parameters.getOriginalFile());
               seenNames.addAll(PyNames.getBuiltinMethods(languageLevel).keySet());
               for (PyClass ancestor : containingClass.getAncestorClasses(null)) {
                 for (PyFunction superMethod : ancestor.getMethods()) {
                   if (!seenNames.contains(superMethod.getName())) {
                     StringBuilder builder = new StringBuilder();
                     builder.append(superMethod.getName());
                     if (!(nextElement instanceof PyParameterList)) {
                       builder.append(superMethod.getParameterList().getText());
                       if (superMethod.getAnnotation() != null) {
                         builder.append(" ")
                           .append(superMethod.getAnnotation().getText())
                           .append(":");
                       }
                       else if (superMethod.getTypeComment() != null) {
                         builder.append(":  ")
                           .append(superMethod.getTypeComment().getText());
                       }
                       else {
                         builder.append(":");
                       }
                     }
                     LookupElementBuilder element = LookupElementBuilder.create(builder.toString());
                     result.addElement(TailTypeDecorator.withTail(element, TailType.NONE));
                     seenNames.add(superMethod.getName());
                   }
                 }
               }
             }
           });
  }
}
