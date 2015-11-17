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
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class PyStringFormatCompletionContributor extends CompletionContributor {
  public PyStringFormatCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement().inside(PyReferenceExpression.class),
      new FormatArgumentsCompletionProvider()
    );

    extend(
      CompletionType.BASIC,
      psiElement().inside(PyStringLiteralExpression.class),
      new FormattedStringCompletionProvider()
    );
  }

  private static class FormatArgumentsCompletionProvider extends CompletionProvider <CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      PsiElement original = parameters.getOriginalPosition();
      PyReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
      tryToAddElementsFromFormattedString(result, referenceExpression);
    }

    private static void tryToAddElementsFromFormattedString(@NotNull final CompletionResultSet result,
                                                            @Nullable final PyReferenceExpression referenceExpression) {
      PyArgumentList argumentList = PsiTreeUtil.getParentOfType(referenceExpression, PyArgumentList.class);
      if (argumentList != null) {
        PyReferenceExpression pyReferenceExpression = PsiTreeUtil.getPrevSiblingOfType(argumentList, PyReferenceExpression.class);
        PyStringLiteralExpression formattedString = PsiTreeUtil.getChildOfType(pyReferenceExpression, PyStringLiteralExpression.class);
        if (formattedString != null) {
          List<LookupElementBuilder> keys = getLookupBuilders(formattedString);
          result.addAllElements(keys);
        }
      }
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

  private static class FormattedStringCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      PsiElement original = parameters.getOriginalPosition();
      if (isInsideSubstitutionChunk(original, parameters.getOffset())) {
        PyExpression[] arguments = getFormatFunctionKeyWordArguments(original);
        for (PyExpression argument : arguments) {
          result = result.withPrefixMatcher(getPrefix(parameters.getOffset(), argument.getContainingFile()));
          tryToAddKeysFromStarArgument(result, argument);
          tryToAddKeyWordArgument(result, argument);
        }
      }
    }

    private static boolean isInsideSubstitutionChunk(final PsiElement original, final int offset) {
      final PsiElement parent = original.getParent();
      if (parent instanceof PyStringLiteralExpression) {
        Map<String, PyStringFormatParser.SubstitutionChunk> substitutions =
          PyStringFormatParser.getKeywordSubstitutions(PyStringFormatParser.filterSubstitutions(
            PyStringFormatParser.parseNewStyleFormat(((PyStringLiteralExpression)
                                                        (parent)).getStringValue())));
        for (PyStringFormatParser.SubstitutionChunk substitution: substitutions.values()) {
          if (offset >= substitution.getStartIndex() && offset <= substitution.getEndIndex()) {
            return true;
          }
        }
      }
      return false;
    }

    @NotNull
    private static PyExpression[] getFormatFunctionKeyWordArguments(PsiElement original) {
      PsiElement pyReferenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
      PyArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(pyReferenceExpression, PyArgumentList.class);
      if (argumentList != null) {
        return argumentList.getArguments();
      }
      return PyExpression.EMPTY_ARRAY;
    }

    private static void tryToAddKeysFromStarArgument(@NotNull CompletionResultSet result, @NotNull final PyExpression arg) {
      if (arg instanceof PyStarArgument) {
        PyDictLiteralExpression dict = PsiTreeUtil.getChildOfType(arg, PyDictLiteralExpression.class);
        if (dict != null) {
          for (PyKeyValueExpression keyValue: dict.getElements()) {
            if (keyValue.getKey() instanceof PyStringLiteralExpression) {
              String key = ((PyStringLiteralExpression) keyValue.getKey()).getStringValue();
              addElementToResult(result, key);
            }
          }
        }
      }
    }

    private static void tryToAddKeyWordArgument(@NotNull final CompletionResultSet result, @NotNull final PyExpression arg) {
      if (arg instanceof PyKeywordArgument) {
        final String keyword = ((PyKeywordArgument)arg).getKeyword();
        addElementToResult(result, keyword);
        result.stopHere();
      }
    }

    @NotNull
    private static CompletionResultSet addElementToResult(@NotNull CompletionResultSet result,String element) {
      result.addElement(LookupElementBuilder
                          .create(element)
                          .withTypeText("dict keys")
                          .withIcon(PlatformIcons.VARIABLE_ICON));
      return result;
    }

  }

  private static String getPrefix(int offset, PsiFile file) {
    if (offset > 0) {
      offset--;
    }
    final String text = file.getText();
    StringBuilder prefixBuilder = new StringBuilder();
    while(offset > 0 && Character.isLetterOrDigit(text.charAt(offset))) {
      prefixBuilder.insert(0, text.charAt(offset));
      offset--;
    }
    return prefixBuilder.toString();
  }

}
