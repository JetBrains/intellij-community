package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.ui.DebuggerEditorImpl;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 6:47:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class TextWithImportsImpl implements TextWithImports{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.TextWithImportsImpl");

  private final String myText;
  private final String myImports;
  private final CodeFragmentFactory myCodeFragmentFactory;

  public static final CodeFragmentFactory EXPRESSION_FACTORY = new CodeFragmentFactory() {
    public PsiCodeFragment createCodeFragment(TextWithImportsImpl item, PsiElement context, Project project) {
      String text = item.getText();
      if (StringUtil.endsWithChar(text, ';')) {
        text = text.substring(0, text.length() - 1);
      }
      PsiExpressionCodeFragment expressionCodeFragment = PsiManager.getInstance(project).getElementFactory().createExpressionCodeFragment(
              text, context, true);
      initCodeFragment(item, expressionCodeFragment);
      return expressionCodeFragment;
    }
  };
  public static final CodeFragmentFactory CODE_BLOCK_FACTORY = new CodeFragmentFactory() {
    public PsiCodeFragment createCodeFragment(TextWithImportsImpl item, PsiElement context, Project project) {
      PsiCodeFragment codeFragment = PsiManager.getInstance(project).getElementFactory().createCodeBlockCodeFragment(
              item.getText(), context, true);

      initCodeFragment(item, codeFragment);
      return codeFragment;
    }
  };

  public final static TextWithImportsImpl EMPTY = createExpressionText("");

  private static final Object IS_DEBUGGER_EDITOR = "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR";

  public TextWithImportsImpl (CodeFragmentFactory factory, String text, String imports) {
    LOG.assertTrue(factory != null);
    myCodeFragmentFactory = factory;
    myText = text;
    myImports = imports;
    LOG.assertTrue(myImports != null);
  }

  public TextWithImportsImpl (CodeFragmentFactory factory, String text) {
    LOG.assertTrue(factory != null);
    myCodeFragmentFactory = factory;
    text = text.trim();
    int separator = text.indexOf(DebuggerEditorImpl.SEPARATOR);
    if(separator != -1){
      myText = text.substring(0, separator);
      myImports = text.substring(separator + 1);
    } else {
      myText = text;
      myImports = "";
    }
    LOG.assertTrue(myImports != null);
  }

  public String toString() {
    return myText;
  }

  public String saveToString() {
    return myImports.equals("") ? myText : myText + DebuggerEditorImpl.SEPARATOR + myImports;
  }

  public String getText() {
    return myText;
  }

  public String getImports() {
    return myImports;
  }

  public boolean equals(Object object) {
    if(!(object instanceof TextWithImportsImpl)) return false;
    TextWithImportsImpl item = ((TextWithImportsImpl)object);
    return Comparing.equal(item.myText, myText) && Comparing.equal(item.myImports, myImports);
  }

  public PsiCodeFragment createCodeFragment(PsiElement context, Project project){
    return myCodeFragmentFactory.createCodeFragment(this, context, project);
  }

  public int hashCode() {
    return myText.hashCode();
  }

  public static TextWithImportsImpl createExpressionText(PsiExpression expression) {
    PsiFile containingFile = expression.getContainingFile();
    if(containingFile instanceof PsiExpressionCodeFragment) {
      return new TextWithImportsImpl(EXPRESSION_FACTORY, expression.getText(), ((PsiCodeFragment)containingFile).importsToString());
    }
    return createExpressionText(expression.getText());
  }
  public static TextWithImportsImpl createExpressionText(String expression) {
    return new TextWithImportsImpl(EXPRESSION_FACTORY, expression);
  }

  public static TextWithImportsImpl createCodeBlockText(String expression) {
    return new TextWithImportsImpl(CODE_BLOCK_FACTORY, expression);
  }

  private static void initCodeFragment(TextWithImportsImpl item, PsiCodeFragment codeFragment) {
    if(item.getImports() != null) {
      codeFragment.addImportsFromString(item.getImports());
    }
    codeFragment.setEverythingAcessible(true);
    codeFragment.putUserData(DebuggerExpressionComboBox.KEY, IS_DEBUGGER_EDITOR);
  }

  public boolean isEmpty() {
    return "".equals(getText().trim());
  }

  public TextWithImportsImpl createText(String newText) {
    return new TextWithImportsImpl(EXPRESSION_FACTORY, newText, getImports());
  }

  public CodeFragmentFactory getFactory() {
    return myCodeFragmentFactory;
  }
}
