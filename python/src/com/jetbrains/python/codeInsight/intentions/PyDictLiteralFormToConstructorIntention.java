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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Intention to convert dict literal expression to dict constructor if the keys are all string constants on a literal dict.
 * For instance,
 * {} -> dict
 * {'a': 3, 'b': 5} -> dict(a=3, b=5)
 * {a: 3, b: 5} -> no transformation
 */
public class PyDictLiteralFormToConstructorIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.dict.literal.to.dict.constructor");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.dict.literal.to.dict.constructor");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyDictLiteralExpression dictExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyDictLiteralExpression.class);

    if (dictExpression != null) {
      PyKeyValueExpression[] elements = dictExpression.getElements();
      if (elements.length != 0) {
        for (PyKeyValueExpression element : elements) {
          PyExpression key = element.getKey();
          if (! (key instanceof PyStringLiteralExpression)) return false;
          String str = ((PyStringLiteralExpression)key).getStringValue();
          if (PyNames.isReserved(str)) return false;

          if(str.length() == 0 || Character.isDigit(str.charAt(0))) return false;
          if (!StringUtil.isJavaIdentifier(str)) return false;
        }
      }
      return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyDictLiteralExpression dictExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyDictLiteralExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (dictExpression != null) {
      replaceDictLiteral(dictExpression, elementGenerator);
    }
  }

  private static void replaceDictLiteral(PyDictLiteralExpression dictExpression, PyElementGenerator elementGenerator) {
    PyExpression[] argumentList = dictExpression.getElements();
    StringBuilder stringBuilder = new StringBuilder("dict(");
    int size = argumentList.length;
    for (int i = 0; i != size; ++i) {
      PyExpression argument = argumentList[i];
      if (argument instanceof PyKeyValueExpression) {
        PyExpression key = ((PyKeyValueExpression)argument).getKey();
        PyExpression value = ((PyKeyValueExpression)argument).getValue();
        if (key instanceof PyStringLiteralExpression && value != null) {
          stringBuilder.append(((PyStringLiteralExpression)key).getStringValue());
          stringBuilder.append("=");
          stringBuilder.append(value.getText());
          if (i != size-1)
            stringBuilder.append(", ");
        }
      }
    }
    stringBuilder.append(")");
    PyCallExpression callExpression = (PyCallExpression)elementGenerator.createFromText(LanguageLevel.forElement(dictExpression),
                                                     PyExpressionStatement.class, stringBuilder.toString()).getExpression();
    dictExpression.replace(callExpression);
  }
}
