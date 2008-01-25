package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XDebuggerEditorBase {
  private final Project myProject;
  private final XDebuggerEditorsProvider myDebuggerEditorsProvider;

  protected XDebuggerEditorBase(final Project project, XDebuggerEditorsProvider debuggerEditorsProvider) {
    myProject = project;
    myDebuggerEditorsProvider = debuggerEditorsProvider;
  }

  public abstract JComponent getComponent();

  public abstract void setText(String text);

  public abstract String getText();

  public XDebuggerEditorsProvider getEditorsProvider() {
    return myDebuggerEditorsProvider;
  }

  public Project getProject() {
    return myProject;
  }

  protected Document createDocument(final String text) {
    return getEditorsProvider().createDocument(getProject(), text);
  }
}
