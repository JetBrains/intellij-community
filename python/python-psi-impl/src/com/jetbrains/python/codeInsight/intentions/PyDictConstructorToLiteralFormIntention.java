// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * Intention to convert dict constructor to dict literal expression with only explicit keyword arguments
 * For instance,
 * dict() -> {}
 * dict(a=3, b=5) -> {'a': 3, 'b': 5}
 * dict(foo) -> no transformation
 * dict(**foo) -> no transformation
 */
public class PyDictConstructorToLiteralFormIntention extends PyBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.dict.constructor.to.dict.literal");
  }

  @Override
  @NotNull
  public String getText() {
    return PyPsiBundle.message("INTN.convert.dict.constructor.to.dict.literal");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyCallExpression expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyCallExpression.class);

    if (expression != null && expression.isCalleeText("dict")) {
      final TypeEvalContext context = TypeEvalContext.codeAnalysis(file.getProject(), file);
      PyType type = context.getType(expression);
      if (type != null && type.isBuiltin()) {
        PyExpression[] argumentList = expression.getArguments();
        for (PyExpression argument : argumentList) {
          if (!(argument instanceof PyKeywordArgument)) return false;
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyCallExpression expression =
          PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyCallExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (expression != null) {
      replaceDictConstructor(expression, elementGenerator);
    }
  }

  private static void replaceDictConstructor(PyCallExpression expression, PyElementGenerator elementGenerator) {
    PyExpression[] argumentList = expression.getArguments();
    StringBuilder stringBuilder = new StringBuilder();

    int size = argumentList.length;

    for (int i = 0; i != size; ++i) {
      PyExpression argument = argumentList[i];
      if (argument instanceof PyKeywordArgument) {
        stringBuilder.append("'");
        stringBuilder.append(((PyKeywordArgument)argument).getKeyword());
        stringBuilder.append("' : ");
        stringBuilder.append(((PyKeywordArgument)argument).getValueExpression().getText());
        if (i != size-1)
          stringBuilder.append(",");
      }

    }
    PyDictLiteralExpression dict = (PyDictLiteralExpression)elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class,
                                                                                            "{" + stringBuilder + "}").getExpression();
    expression.replace(dict);
  }
}
