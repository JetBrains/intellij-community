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
import com.intellij.util.ObjectUtils;
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
import static com.intellij.patterns.StandardPatterns.or;

public class PyStringFormatCompletionContributor extends CompletionContributor {
  public PyStringFormatCompletionContributor() {
    extend(
      CompletionType.BASIC,
      or(psiElement().inside(PyArgumentList
                            .class), psiElement().inside(PyStringLiteralExpression.class)),
      new FormattedStringCompletionProvider()
    );
  }


  private static class FormattedStringCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      PsiElement original = parameters.getOriginalPosition();
      if (original != null) {
        PsiElement parent = original.getParent();

        if (parent instanceof PyStringLiteralExpression) {
          int stringOffset = parameters.getOffset() - parameters.getPosition().getTextRange().getStartOffset();
          if (isInsideSubstitutionChunk((PyStringLiteralExpression)parent,
                                        stringOffset)) {
          PyExpression[] arguments = getFormatFunctionKeyWordArguments(original);
          for (PyExpression argument : arguments) {
            result = result.withPrefixMatcher(getPrefix(parameters.getOffset(), argument.getContainingFile()));
            tryToAddKeysFromStarArgument(result, argument);
            tryToAddKeyWordArgument(result, argument);
          }
          }
        }
        else if (PyUtil.instanceOf(parent, PyKeywordArgument.class, PyReferenceExpression.class)) {
          PyArgumentList argumentList = PsiTreeUtil.getParentOfType(original, PyArgumentList.class);
          result = result.withPrefixMatcher(getPrefix(parameters.getOffset(), parent.getContainingFile()));
          tryToAddElementsFromFormattedString(result, argumentList);
        }
      }


    }

    private static boolean isInsideSubstitutionChunk(@NotNull final PyStringLiteralExpression expression, final int offset) {
        List<PyStringFormatParser.SubstitutionChunk> substitutions = PyStringFormatParser.filterSubstitutions(
            PyStringFormatParser.parseNewStyleFormat(expression.getStringValue()));
        for (PyStringFormatParser.SubstitutionChunk substitution: substitutions) {
          if (offset >= substitution.getStartIndex() && offset <= substitution.getEndIndex()) {
            return true;
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
        PyDictLiteralExpression dict = ObjectUtils.chooseNotNull(PsiTreeUtil.getChildOfType(arg, PyDictLiteralExpression.class),
                                         getDictFromReference(arg));
        if (dict != null) {
          for (PyKeyValueExpression keyValue: dict.getElements()) {
            if (keyValue.getKey() instanceof PyStringLiteralExpression) {
              String key = ((PyStringLiteralExpression) keyValue.getKey()).getStringValue();
              addElementToResult(result, key);
            }
          }
        }
        else {
          getDictFromReference(arg);

        }
      }
    }

    private static PyDictLiteralExpression getDictFromReference(@NotNull PyExpression arg) {
      PyReferenceExpression referenceExpression = PsiTreeUtil.getChildOfType(arg, PyReferenceExpression.class);
      if (referenceExpression != null) {
        PsiElement resolveResult = referenceExpression.getReference().resolve();
        if (resolveResult instanceof PyTargetExpression) {
          PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(resolveResult, PyAssignmentStatement.class);
          return PsiTreeUtil.getChildOfType(assignmentStatement, PyDictLiteralExpression.class);
        }
      }
      return null;
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
                          .withTypeText("arg")
                          .withIcon(PlatformIcons.VARIABLE_ICON));
      return result;
    }

    private static void tryToAddElementsFromFormattedString(@NotNull final CompletionResultSet result,
                                                            @Nullable final PyArgumentList argumentList) {
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
        keys.add(LookupElementBuilder.create(chunk).withTypeText("arg").withIcon(PlatformIcons.VARIABLE_ICON));
      }
      return keys;
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
