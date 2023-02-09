// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * User: catherine
 * <p/>
 * Complete known keys for dictionaries
 */
public class PyDictKeyNamesCompletionContributor extends CompletionContributor implements DumbAware {
  private static final String DEFAULT_QUOTE = "\"";

  public PyDictKeyNamesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement().inside(PySubscriptionExpression.class),
      new CompletionProvider<>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
          final PsiElement original = parameters.getOriginalPosition();
          final int offset = parameters.getOffset();
          if (original == null) return;
          final CompletionResultSet dictCompletion = createResult(original, result, offset);

          final PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(original, PySubscriptionExpression.class);
          if (subscription == null) return;
          final PyExpression operand = subscription.getOperand();
          if (addCompletionIfOperandIsTypedDict(operand, subscription.getIndexExpression(), dictCompletion)) {
            return;
          }
          if (operand instanceof PyReferenceExpression) {
            final PyExpression resolvedElement = PyResolveUtil.fullResolveLocally((PyReferenceExpression)operand);
            if (resolvedElement instanceof PyDictLiteralExpression) {
              addDictLiteralKeys((PyDictLiteralExpression)resolvedElement, dictCompletion);
              addAdditionalKeys(parameters.getOriginalFile(), operand, dictCompletion);
            }
            if (resolvedElement instanceof PyCallExpression) {
              addDictConstructorKeys((PyCallExpression)resolvedElement, dictCompletion);
              addAdditionalKeys(parameters.getOriginalFile(), operand, dictCompletion);
            }
          }
        }
      }
    );
  }

  /**
   * Add index expression completion if an operand is a TypedDict
   *
   * @return true if an operand is a TypedDict
   */
  private static boolean addCompletionIfOperandIsTypedDict(@NotNull final PyExpression operand,
                                                           @Nullable final PyExpression index,
                                                           @NotNull final CompletionResultSet dictCompletion) {
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(operand.getProject(), operand.getContainingFile());
    final PyType type = typeEvalContext.getType(operand);
    if (type instanceof PyTypedDictType && !((PyTypedDictType)type).isInferred()) {
      String quote = DEFAULT_QUOTE;
      if (index instanceof PyStringLiteralExpression) {
        quote = ((PyStringLiteralExpression)index).getStringElements().get(0).getQuote();
      }
      for (String key : ((PyTypedDictType)type).getFields().keySet()) {
        dictCompletion.addElement(createElement(quote + key + quote));
      }
      return true;
    }
    return false;
  }

  /**
   * create completion result with prefix matcher if needed
   *
   * @param original is original element
   * @param result   is initial completion result
   */
  private static CompletionResultSet createResult(@NotNull final PsiElement original,
                                                  @NotNull final CompletionResultSet result,
                                                  final int offset) {
    PyStringLiteralExpression prevElement = PsiTreeUtil.getPrevSiblingOfType(original, PyStringLiteralExpression.class);
    if (prevElement != null) {
      ASTNode prevNode = prevElement.getNode();
      if (prevNode != null) {
        if (prevNode.getElementType() != PyTokenTypes.LBRACKET) {
          return result.withPrefixMatcher(findPrefix(prevElement, offset));
        }
      }
    }
    final PsiElement parentElement = original.getParent();
    if (parentElement != null) {
      if (parentElement instanceof PyStringLiteralExpression) {
        return result.withPrefixMatcher(findPrefix((PyElement)parentElement, offset));
      }
    }
    final PyNumericLiteralExpression number = PsiTreeUtil.findElementOfClassAtOffset(original.getContainingFile(),
                                                                                     offset - 1, PyNumericLiteralExpression.class, false);
    if (number != null) {
      return result.withPrefixMatcher(findPrefix(number, offset));
    }
    return result;
  }

  /**
   * finds prefix. For *'str'* returns just *'str*.
   *
   * @param element to find prefix of
   * @return prefix
   */
  private static String findPrefix(final PyElement element, final int offset) {
    return TextRange.create(element.getTextRange().getStartOffset(), offset).substring(element.getContainingFile().getText());
  }

  /**
   * add keys to completion result from dict constructor
   */
  private static void addDictConstructorKeys(final PyCallExpression dictConstructor, final CompletionResultSet result) {
    final PyExpression callee = dictConstructor.getCallee();
    if (callee == null) return;
    final String name = callee.getText();
    if ("dict".equals(name)) {
      final TypeEvalContext context = TypeEvalContext.codeCompletion(callee.getProject(), callee.getContainingFile());
      final PyType type = context.getType(dictConstructor);
      if (type != null && type.isBuiltin()) {
        final PyArgumentList list = dictConstructor.getArgumentList();
        if (list == null) return;
        final PyExpression[] argumentList = list.getArguments();
        for (final PyExpression argument : argumentList) {
          if (argument instanceof PyKeywordArgument) {
            result.addElement(createElement(DEFAULT_QUOTE + ((PyKeywordArgument)argument).getKeyword() + DEFAULT_QUOTE));
          }
        }
      }
    }
  }

  /**
   * add keys from assignment statements
   * For instance, dictionary['b']=b
   *
   * @param file    to get additional keys
   * @param operand is operand of origin element
   * @param result  is completion result set
   */
  private static void addAdditionalKeys(final PsiFile file, final PsiElement operand, final CompletionResultSet result) {
    Collection<PySubscriptionExpression> subscriptionExpressions = PsiTreeUtil.findChildrenOfType(file, PySubscriptionExpression.class);
    for (PySubscriptionExpression expr : subscriptionExpressions) {
      if (expr.getOperand().getText().equals(operand.getText())) {
        final PsiElement parent = expr.getParent();
        if (parent instanceof PyAssignmentStatement) {
          if (expr.equals(((PyAssignmentStatement)parent).getLeftHandSideExpression())) {
            PyExpression key = expr.getIndexExpression();
            if (key != null) {
              boolean addHandler = PsiTreeUtil.findElementOfClassAtRange(file, key.getTextRange().getStartOffset(),
                                                                         key.getTextRange().getEndOffset(),
                                                                         PyStringLiteralExpression.class) != null;
              result.addElement(createElement(key.getText(), addHandler));
            }
          }
        }
      }
    }
  }

  /**
   * add keys from dict literal expression
   */
  public static void addDictLiteralKeys(final PyDictLiteralExpression dict, final CompletionResultSet result) {
    PyKeyValueExpression[] keyValues = dict.getElements();
    for (PyKeyValueExpression expression : keyValues) {
      boolean addHandler = PsiTreeUtil.findElementOfClassAtRange(dict.getContainingFile(), expression.getTextRange().getStartOffset(),
                                                                 expression.getTextRange().getEndOffset(),
                                                                 PyStringLiteralExpression.class) != null;
      result.addElement(createElement(expression.getKey().getText(), addHandler));
    }
  }

  private static LookupElementBuilder createElement(final String key) {
    return createElement(key, true);
  }

  private static LookupElementBuilder createElement(final String key, final boolean addHandler) {
    LookupElementBuilder item;
    item = LookupElementBuilder
      .create(key)
      .withTypeText("dict key")
      .withIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter));

    if (addHandler) {
      item = item.withInsertHandler(new InsertHandler<>() {
        @Override
        public void handleInsert(@NotNull final InsertionContext context, @NotNull final LookupElement item) {
          final PyStringLiteralExpression str = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(),
                                                                                       PyStringLiteralExpression.class, false);
          if (str != null) {
            final boolean isDictKeys = PsiTreeUtil.getParentOfType(str, PySubscriptionExpression.class) != null;
            if (isDictKeys) {
              final int off = context.getStartOffset() + str.getTextLength();
              final PsiElement element = context.getFile().findElementAt(off);
              final boolean atRBrace = element == null || element.getNode().getElementType() == PyTokenTypes.RBRACKET;
              final boolean badQuoting =
                (!StringUtil.startsWithChar(str.getText(), '\'') || !StringUtil.endsWithChar(str.getText(), '\'')) &&
                (!StringUtil.startsWithChar(str.getText(), '"') || !StringUtil.endsWithChar(str.getText(), '"'));
              if (badQuoting || !atRBrace) {
                final Document document = context.getEditor().getDocument();
                final int offset = context.getTailOffset();
                document.deleteString(offset - 1, offset);
              }
            }
          }
        }
      });
    }
    return item;
  }
}
