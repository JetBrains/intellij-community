package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.ui.DebuggerEditorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
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

  private String myText;
  private final String myImports;
  private final CodeFragmentFactory myCodeFragmentFactory;

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

  public String toString() {
    return getText();
  }

  public PsiCodeFragment createCodeFragment(PsiElement context, Project project){
    return myCodeFragmentFactory.createCodeFragment(this, context, project);
  }

  public int hashCode() {
    return myText.hashCode();
  }

  public boolean isEmpty() {
    return "".equals(getText().trim());
  }

  public void setText(String newText) {
    myText = newText;
  }

  public boolean isExpressionText() {
    return myCodeFragmentFactory == EvaluationManagerImpl.EXPRESSION_FACTORY;
  }
}
