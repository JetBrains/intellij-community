package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
public class PyDictConstructorIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.dict");
  }
  
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyCallExpression expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyCallExpression.class);
    
    if (expression != null) {
      String name = expression.getCallee().getText();
      if ("dict".equals(name)) {
        PyType type = expression.getType(TypeEvalContext.fast());
        if (type != null) {
          if (type.isBuiltin()) {
            PyExpression[] argumentList = expression.getArgumentList().getArguments();
            for (PyExpression argument : argumentList) {
              if (!(argument instanceof PyKeywordArgument)) return false;
            }
            setText(PyBundle.message("INTN.convert.dict.constructor.to.dict.literal"));
            return true;
          }
        }
      }
    }
    PyDictLiteralExpression dictExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyDictLiteralExpression.class);

    if (dictExpression != null) {
      PyKeyValueExpression[] elements = dictExpression.getElements();
      if (elements.length != 0) {
        for (PyKeyValueExpression element : elements) {
          if (! (element.getKey() instanceof PyStringLiteralExpression)) return false;
        }
      }
      setText(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
      return true;
    }

    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyCallExpression expression =
          PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyCallExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (expression != null) {
      replaceDictConstructor(expression, elementGenerator);
      return;
    }
    PyDictLiteralExpression dictExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyDictLiteralExpression.class);
    if (dictExpression != null) {
      replaceDictLiteral(dictExpression, elementGenerator);
    }
  }

  private static void replaceDictConstructor(PyCallExpression expression, PyElementGenerator elementGenerator) {
    PyExpression[] argumentList = expression.getArgumentList().getArguments();
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
    PyExpressionStatement dict = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class,
                                                "{" + stringBuilder.toString() + "}");
    expression.replace(dict);
  }

  private static void replaceDictLiteral(PyDictLiteralExpression dictExpression, PyElementGenerator elementGenerator) {
    PyExpression[] argumentList = dictExpression.getElements();
    StringBuilder stringBuilder = new StringBuilder();
    int size = argumentList.length;
    for (int i = 0; i != size; ++i) {
      PyExpression argument = argumentList[i];
      if (argument instanceof PyKeyValueExpression) {
        PyExpression key = ((PyKeyValueExpression)argument).getKey();
        if (key instanceof PyStringLiteralExpression)
          stringBuilder.append(((PyStringLiteralExpression)key).getStringValue());
        stringBuilder.append("=");
        stringBuilder.append(((PyKeyValueExpression)argument).getValue().getText());
        if (i != size-1)
          stringBuilder.append(", ");
      }
    }
    PyStatement callExpression = elementGenerator.createFromText(LanguageLevel.forElement(dictExpression), PyStatement.class,
                                                      "dict(" + stringBuilder.toString() + ")");
    dictExpression.replace(callExpression);
  }
}
