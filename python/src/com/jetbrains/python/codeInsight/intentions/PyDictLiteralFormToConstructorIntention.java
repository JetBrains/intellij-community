package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

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
  static Set<String> KEYWORDS = new HashSet<String>();
  static {
    KEYWORDS.add("class");
    KEYWORDS.add("as");
    KEYWORDS.add("assert");
    KEYWORDS.add("and");
    KEYWORDS.add("break");
    KEYWORDS.add("continue");
    KEYWORDS.add("def");
    KEYWORDS.add("del");
    KEYWORDS.add("else");
    KEYWORDS.add("if");
    KEYWORDS.add("elif");
    KEYWORDS.add("except");
    KEYWORDS.add("exec");
    KEYWORDS.add("finally");
    KEYWORDS.add("for");
    KEYWORDS.add("from");
    KEYWORDS.add("global");
    KEYWORDS.add("import");
    KEYWORDS.add("in");
    KEYWORDS.add("is");
    KEYWORDS.add("lambda");
    KEYWORDS.add("not");
    KEYWORDS.add("or");
    KEYWORDS.add("pass");
    KEYWORDS.add("print");
    KEYWORDS.add("raise");
    KEYWORDS.add("return");
    KEYWORDS.add("try");
    KEYWORDS.add("with");
    KEYWORDS.add("while");
    KEYWORDS.add("yield");
    KEYWORDS.add("None");
    KEYWORDS.add("True");
    KEYWORDS.add("False");

  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.dict.literal.to.dict.constructor");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.dict.literal.to.dict.constructor");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {

    PyDictLiteralExpression dictExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyDictLiteralExpression.class);

    if (dictExpression != null) {
      PyKeyValueExpression[] elements = dictExpression.getElements();
      boolean canConvert = true;
      if (elements.length != 0) {
        for (PyKeyValueExpression element : elements) {
          PyExpression key = element.getKey();
          if (! (key instanceof PyStringLiteralExpression)) return false;
          String str = ((PyStringLiteralExpression)key).getStringValue();
          if (KEYWORDS.contains(str)) return false;
          if(str.length() == 0 || Character.isDigit(str.charAt(0))) return false;
          try {
            Integer.parseInt(str) ;
            canConvert = false;
          } catch (NumberFormatException e) {
            // pass
          }
        }
      }
      if (canConvert) return true;
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
        if (key instanceof PyStringLiteralExpression)
          stringBuilder.append(((PyStringLiteralExpression)key).getStringValue());
        stringBuilder.append("=");
        stringBuilder.append(((PyKeyValueExpression)argument).getValue().getText());
        if (i != size-1)
          stringBuilder.append(", ");
      }
    }
    stringBuilder.append(")");
    PyCallExpression callExpression = (PyCallExpression)elementGenerator.createFromText(LanguageLevel.forElement(dictExpression),
                                                     PyExpressionStatement.class, stringBuilder.toString()).getExpression();
    dictExpression.replace(callExpression);
  }
}
