package com.intellij.debugger.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.ui.*;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.DebuggerContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;
import org.jdom.Element;

import java.util.HashMap;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerUtilsImpl extends DebuggerUtilsEx{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerUtilsImpl");

  public String getComponentName() {
    return "DebuggerUtils";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue, StackFrameContext context)
    throws EvaluateException {
    return DebuggerTreeNodeExpression.substituteThis(expressionWithThis, howToEvaluateThis, howToEvaluateThisValue);
  }

  public EvaluatorBuilder getEvaluatorBuilder() {
    return EvaluatorBuilderImpl.getInstance();
  }

  public DebuggerTreeNode getSelectedNode(DataContext context) {
    return DebuggerAction.getSelectedNode(context);
  }

  public DebuggerContextImpl getDebuggerContext(DataContext context) {
    return DebuggerAction.getDebuggerContext(context);
  }

  public Element writeTextWithImports(TextWithImports text) {
    Element element = new Element("TextWithImports");
    TextWithImportsImpl textImpl = (TextWithImportsImpl) text;

    element.setAttribute("text", textImpl.saveToString());
    element.setAttribute("type", textImpl.getFactory() ==  TextWithImportsImpl.EXPRESSION_FACTORY ? "expression" : "code fragment");
    return element;
  }

  public TextWithImports readTextWithImports(Element element) {
    LOG.assertTrue("TextWithImports".equals(element.getName()));

    String text = element.getAttributeValue("text");
    CodeFragmentFactory factory = "expression".equals(element.getAttributeValue("type")) ? TextWithImportsImpl.EXPRESSION_FACTORY : TextWithImportsImpl.CODE_BLOCK_FACTORY;
    return new TextWithImportsImpl(factory, text);
  }

  public void writeTextWithImports(Element root, String name, TextWithImports value) {
    LOG.assertTrue(((TextWithImportsImpl)value).getFactory() == TextWithImportsImpl.EXPRESSION_FACTORY);
    JDOMExternalizerUtil.writeField(root, name, ((TextWithImportsImpl)value).saveToString());
  }

  public TextWithImports readTextWithImports(Element root, String name) {
    String s = JDOMExternalizerUtil.readField(root, name);
    if(s == null) return null;
    return new TextWithImportsImpl(TextWithImportsImpl.EXPRESSION_FACTORY, s);
  }

  public TextWithImports createExpressionText(PsiExpression expression) {
    return TextWithImportsImpl.createExpressionText(expression);
  }

  public TextWithImports createExpressionWithImports(String expression) {
    return new TextWithImportsImpl(TextWithImportsImpl.EXPRESSION_FACTORY, expression);
  }

  public PsiElement getContextElement(StackFrameContext context) {
    return PositionUtil.getContextElement(context);
  }

  public PsiClass chooseClassDialog(String title, Project project) {
    TreeClassChooserDialog dialog = new TreeClassChooserDialog(title, project);
    dialog.show();
    return dialog.getSelectedClass();
  }

  public CompletitionEditor createEditor(Project project, PsiElement context, String recentsId) {
    return new DebuggerExpressionComboBox(project, context, recentsId);
  }
}
