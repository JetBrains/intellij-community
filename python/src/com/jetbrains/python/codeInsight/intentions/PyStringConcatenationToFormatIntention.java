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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyStringConcatenationToFormatIntention extends BaseIntentionAction {

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.string.concatenation.to.format");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PsiElement element = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);

    if (element == null) {
      return false;
    }
    while (element.getParent() instanceof PyBinaryExpression) {
      element = element.getParent();
    }

    final Collection<PyElementType> operators = getOperators((PyBinaryExpression)element);
    for (PyElementType operator : operators) {
      if (operator != PyTokenTypes.PLUS) {
        return false;
      }
    }

    final Collection<PyExpression> expressions = getSimpleExpressions((PyBinaryExpression)element);
    if (expressions.size() == 0) {
      return false;
    }
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(element);
    for (PyExpression expression: expressions) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PyStringLiteralExpression)
        continue;
      final PyType type = TypeEvalContext.codeAnalysis(file.getProject(), file).getType(expression);
      final boolean isStringReference = PyTypeChecker.match(cache.getStringType(LanguageLevel.forElement(expression)),
                                                            type, TypeEvalContext.codeAnalysis(file.getProject(), file)) && type != null;
      if (!isStringReference) {
        return false;
      }
    }
    if (LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON27))
      setText(PyBundle.message("INTN.replace.plus.with.str.format"));
    else
      setText(PyBundle.message("INTN.replace.plus.with.format.operator"));
    return true;
  }

  private static Collection<PyExpression> getSimpleExpressions(@NotNull PyBinaryExpression expression) {
    List<PyExpression> res = new ArrayList<>();
    if (expression.getLeftExpression() instanceof PyBinaryExpression) {
      res.addAll(getSimpleExpressions((PyBinaryExpression) expression.getLeftExpression()));
    }
    else {
      res.add(expression.getLeftExpression());
    }
    if (expression.getRightExpression() instanceof PyBinaryExpression) {
      res.addAll(getSimpleExpressions((PyBinaryExpression) expression.getRightExpression()));
    } else {
      res.add(expression.getRightExpression());
    }
    return res;
  }

  private static Collection<PyElementType> getOperators(@NotNull PyBinaryExpression expression) {
    List<PyElementType> res = new ArrayList<>();
    if (expression.getLeftExpression() instanceof PyBinaryExpression) {
      res.addAll(getOperators((PyBinaryExpression)expression.getLeftExpression()));
    }
    if (expression.getRightExpression() instanceof PyBinaryExpression) {
      res.addAll(getOperators((PyBinaryExpression)expression.getRightExpression()));
    }
    res.add(expression.getOperator());
    return res;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class);

    if (element == null) return;
    final LanguageLevel languageLevel = LanguageLevel.forElement(element);
    final boolean useFormatMethod = languageLevel.isAtLeast(LanguageLevel.PYTHON27);

    NotNullFunction<String,String> escaper = StringUtil.escaper(false, "\"\'\\");
    StringBuilder stringLiteral = new StringBuilder();
    List<String> parameters = new ArrayList<>();
    Pair<String, String> quotes = Pair.create("\"", "\"");
    boolean quotesDetected = false;
    final TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file);
    int paramCount = 0;
    boolean isUnicode = false;
    final PyClassTypeImpl unicodeType = PyBuiltinCache.getInstance(element).getObjectType("unicode");

    for (PyExpression expression : getSimpleExpressions((PyBinaryExpression) element)) {
      if (expression instanceof PyStringLiteralExpression) {
        final PyType type = context.getType(expression);
        if (type != null && type.equals(unicodeType)) {
          isUnicode = true;
        }
        if (!quotesDetected) {
          quotes = PythonStringUtil.getQuotes(expression.getText());
          quotesDetected = true;
        }
        String value = ((PyStringLiteralExpression)expression).getStringValue();
        if (!useFormatMethod) {
          value = value.replace("%", "%%");
        }
        stringLiteral.append(escaper.fun(value));
      } else {
        addParamToString(stringLiteral, paramCount, useFormatMethod);
        parameters.add(expression.getText());
        ++paramCount;
      }
    }
    if (quotes == null)
      quotes = Pair.create("\"", "\"");
    stringLiteral.insert(0, quotes.getFirst());
    if (isUnicode && !quotes.getFirst().toLowerCase().contains("u"))
      stringLiteral.insert(0, "u");
    stringLiteral.append(quotes.getSecond());

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    if (!parameters.isEmpty()) {
      if (useFormatMethod) {
        stringLiteral.append(".format(").append(StringUtil.join(parameters, ",")).append(")");

      }
      else {
        final String paramString = parameters.size() > 1? "(" + StringUtil.join(parameters, ",") +")"
                                                       : StringUtil.join(parameters, ",");
        stringLiteral.append(" % ").append(paramString);
      }
      final PyExpression expression = elementGenerator.createFromText(LanguageLevel.getDefault(),
                                                                PyExpressionStatement.class, stringLiteral.toString()).getExpression();
      element.replace(expression);
    }
    else {
      PyStringLiteralExpression stringLiteralExpression =
        elementGenerator.createStringLiteralAlreadyEscaped(stringLiteral.toString());
      element.replace(stringLiteralExpression);
    }
  }

  private static void addParamToString(StringBuilder stringLiteral, int i, boolean useFormatMethod) {
    if (useFormatMethod)
      stringLiteral.append("{").append(i).append("}");
    else
      stringLiteral.append("%s");
  }
}
