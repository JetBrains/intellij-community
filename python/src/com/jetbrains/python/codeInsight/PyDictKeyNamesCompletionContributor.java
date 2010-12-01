package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Icons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * User: catherine
 *
 * Complete known keys for dictionaries
 */
public class PyDictKeyNamesCompletionContributor extends PySeeingOriginalCompletionContributor {
  private static final FilterPattern DICT_KEY = new FilterPattern(
    new InSequenceFilter(psiElement(PySubscriptionExpression.class))
  );

  public PyDictKeyNamesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(DICT_KEY)
     ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          PsiElement original = parameters.getOriginalPosition();
          PsiElement operand = PsiTreeUtil.getParentOfType(original, PySubscriptionExpression.class).getOperand();
          if (operand != null) {
            PsiReference reference = operand.getReference();
            if (reference != null) {
              PsiElement resolvedElement = reference.resolve();
              if (resolvedElement instanceof PyTargetExpression) {
                PyDictLiteralExpression dict = PsiTreeUtil.getNextSiblingOfType(resolvedElement, PyDictLiteralExpression.class);
                if (dict != null) {
                  addDictLiteralKeys(dict, result);
                  PsiFile file = parameters.getOriginalFile();
                  addAdditionalKeys(file, operand, result);
                }
                PyCallExpression dictConstructor = PsiTreeUtil.getNextSiblingOfType(resolvedElement, PyCallExpression.class);
                if (dictConstructor != null) {
                  addDictConstructorKeys(dictConstructor, result);
                  PsiFile file = parameters.getOriginalFile();
                  addAdditionalKeys(file, operand, result);
                }
              }
            }
          }
        }

        /**
         * add keys to completion result from dict constructor
         */
        private void addDictConstructorKeys(PyCallExpression dictConstructor, CompletionResultSet result) {
          String name = dictConstructor.getCallee().getText();
          if ("dict".equals(name)) {
            PyType type = dictConstructor.getType(TypeEvalContext.fast());
            if (type != null) {
              if (type.isBuiltin()) {
                PyExpression[] argumentList = dictConstructor.getArgumentList().getArguments();
                for (PyExpression argument : argumentList) {
                  if (argument instanceof PyKeywordArgument) {
                    LookupElementBuilder item;
                    item = LookupElementBuilder
                      .create("'" + ((PyKeywordArgument)argument).getKeyword() + "'")
                      .setTypeText("dict key")
                      .setIcon(Icons.PARAMETER_ICON);
                    result.addElement(item);
                  }
                }
              }
            }
          }
        }

        /**
         * add keys from assignment statements
         * For instance, dictionary['b']=b
         * @param file to get additional keys
         * @param operand is operand of origin element
         * @param result is completion result set
         */
        private void addAdditionalKeys(PsiFile file, PsiElement operand, CompletionResultSet result) {
          PySubscriptionExpression[] subscriptionExpressions = PyUtil.getAllChildrenOfType(file, PySubscriptionExpression.class);
          for (PySubscriptionExpression expr : subscriptionExpressions) {
            if (expr.getOperand().getText().equals(operand.getText())) {
              PsiElement parent = expr.getParent();
              if (parent instanceof PyAssignmentStatement) {
                if (expr.equals(((PyAssignmentStatement)parent).getLeftHandSideExpression())) {
                  PyExpression key = expr.getIndexExpression();
                  if (key != null) {
                    LookupElementBuilder item;
                    item = LookupElementBuilder
                      .create(key.getText())
                      .setTypeText("dict key")
                      .setIcon(Icons.PARAMETER_ICON);
                    result.addElement(item);
                  }
                }
              }
            }
          }
        }

        /**
         * add keys from dict literal expression
         */
        private void addDictLiteralKeys(PyDictLiteralExpression dict, CompletionResultSet result) {
          PyKeyValueExpression[] keyValues = dict.getElements();
          for (PyKeyValueExpression expression : keyValues) {
            LookupElementBuilder item;
            item = LookupElementBuilder
              .create(expression.getKey().getText())
              .setTypeText("dict key")
              .setIcon(Icons.PARAMETER_ICON);
            result.addElement(item);
          }
        }
      }
    );
  }
}
