package com.intellij.debugger.ui.tree.render.configurables;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.tree.render.LabelRenderer;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiClass;

import javax.swing.*;
import java.awt.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ClassLabelExpressionConfigurable implements UnnamedConfigurable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.configurables.ClassLabelExpressionConfigurable");

  private final Project                      myProject;
  private final LabelRenderer myRenderer;
  private LabeledComponent<CompletionEditor> myCompletitionEditor;
  private final JPanel myPanel;

  public ClassLabelExpressionConfigurable(Project project, LabelRenderer renderer) {
    LOG.assertTrue(project != null);
    myProject = project;
    myRenderer = renderer;

    myCompletitionEditor = new LabeledComponent<CompletionEditor>();
    PsiClass psiClass = DebuggerUtils.findClass(myRenderer.getClassName(), myProject);
    myCompletitionEditor.setComponent(((DebuggerUtilsEx)DebuggerUtils.getInstance()).createEditor(myProject, psiClass, "ClassLabelExpression"));
    myCompletitionEditor.setText("Node label expression");

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(myCompletitionEditor, BorderLayout.NORTH);
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !myRenderer.getLabelExpression().equals(myCompletitionEditor.getComponent().getText());
  }

  public void apply() throws ConfigurationException {
    myRenderer.setLabelExpression(myCompletitionEditor.getComponent().getText());
  }

  public void reset() {
    myCompletitionEditor.getComponent().setText(myRenderer.getLabelExpression());
  }

  public void disposeUIResources() {
    if (myCompletitionEditor != null) {
      myCompletitionEditor.getComponent().dispose();
      myCompletitionEditor = null;
    }
  }
}
