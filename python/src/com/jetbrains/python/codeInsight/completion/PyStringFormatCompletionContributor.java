/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class PyStringFormatCompletionContributor extends CompletionContributor {
  public PyStringFormatCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement().inside(PyStringLiteralExpression.class),
      new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
          PsiElement original = parameters.getOriginalPosition();
          PsiElement pyReferenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
          PyArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(pyReferenceExpression, PyArgumentList.class);
          if (argumentList == null) return;
          PyExpression[] arguments = argumentList.getArguments();
          for (PyExpression arg: arguments) {
            if (arg instanceof PyStarArgument) {
              PyDictLiteralExpression dict = PsiTreeUtil.getChildOfType(arg, PyDictLiteralExpression.class);
              if (dict == null) return;
              for (PyKeyValueExpression keyValue : dict.getElements()) {
                if (keyValue.getKey() instanceof PyStringLiteralExpression) {
                  String key = ((PyStringLiteralExpression)keyValue.getKey()).getStringValue();
                  result.addElement(LookupElementBuilder
                                      .create(key)
                                      .withTypeText("dict keys")
                                      .withIcon(PlatformIcons.VARIABLE_ICON));
                }
              }
            }
          }
        }
      }
    );

    //extend(
    //  CompletionType.BASIC,
    //  psiElement().inside(PyReferenceExpression.class),
    //  new CompletionProvider<CompletionParameters>() {
    //    @Override
    //    protected void addCompletions(@NotNull CompletionParameters parameters,
    //                                  ProcessingContext context,
    //                                  @NotNull CompletionResultSet result) {
    //      PsiElement original = parameters.getOriginalPosition();
    //      PyReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
    //      PyArgumentList argumentList = PsiTreeUtil.getParentOfType(referenceExpression, PyArgumentList.class);
    //      if (argumentList != null) {
    //        PyReferenceExpression pyReferenceExpression = PsiTreeUtil.getPrevSiblingOfType(argumentList, PyReferenceExpression.class);
    //        PyStringLiteralExpression fromattedString = PsiTreeUtil.getChildOfType(pyReferenceExpression, PyStringLiteralExpression.class);
    //        if (fromattedString != null) {
    //          List<LookupElementBuilder> keys = getLookupBuilders(fromattedString);
    //          result.addAllElements(keys);
    //        }
    //      }
    //      else {
    //        PyArgumentList argumentListDict = PsiTreeUtil.getNextSiblingOfType(referenceExpression, PyArgumentList.class);
    //        PyExpression[] arguments = argumentListDict != null ? argumentListDict.getArguments() : new PyExpression[0];
    //        for (PyExpression argument: arguments) {
    //          PyDictLiteralExpression dict = PsiTreeUtil.getChildOfType(argument, PyDictLiteralExpression.class);
    //          if (dict != null) {
    //            for (PyKeyValueExpression keyValue: dict.getElements()) {
    //              if (keyValue.getKey() instanceof PyStringLiteralExpression) {
    //                String key = ((PyStringLiteralExpression) keyValue.getKey()).getStringValue();
    //                result.addElement(LookupElementBuilder
    //                                    .create(key)
    //                                    .withTypeText("dict keys")
    //                                    .withIcon(PlatformIcons.VARIABLE_ICON));
    //              }
    //            }
    //          }
    //        }
    //      }
    //
    //
    //    }
    //  }
    //);

  }

  @NotNull
  private static List<LookupElementBuilder> getLookupBuilders(PyStringLiteralExpression literalExpression) {
    Map<String, PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.getKeywordSubstitutions(
       PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parseNewStyleFormat(literalExpression.getStringValue())));
    List<LookupElementBuilder> keys = new ArrayList<LookupElementBuilder>();
    for (String chunk: chunks.keySet()) {
      keys.add(LookupElementBuilder.create(chunk).withTypeText("field name").withIcon(PlatformIcons.VARIABLE_ICON));
    }
    return keys;
  }

}
