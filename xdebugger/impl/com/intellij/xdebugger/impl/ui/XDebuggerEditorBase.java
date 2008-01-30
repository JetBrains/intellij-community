package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class XDebuggerEditorBase {
  private final Project myProject;
  private final XDebuggerEditorsProvider myDebuggerEditorsProvider;
  @Nullable private final String myHistoryId;

  protected XDebuggerEditorBase(final Project project, XDebuggerEditorsProvider debuggerEditorsProvider, @Nullable @NonNls String historyId) {
    myProject = project;
    myDebuggerEditorsProvider = debuggerEditorsProvider;
    myHistoryId = historyId;
  }

  public abstract JComponent getComponent();

  public abstract void setText(String text);

  public abstract String getText();

  public abstract JComponent getPreferredFocusedComponent();

  public abstract void selectAll();

  protected void onHistoryChanged() {
  }

  protected List<String> getRecentExpressions() {
    if (myHistoryId != null) {
      return XDebuggerHistoryManager.getInstance(myProject).getRecentExpressions(myHistoryId);
    }
    return Collections.emptyList();
  }

  public void saveTextInHistory() {
    saveTextInHistory(getText());
  }

  public void saveTextInHistory(final String text) {
    if (myHistoryId != null) {
      XDebuggerHistoryManager.getInstance(myProject).addRecentExpression(myHistoryId, text);
      onHistoryChanged();
    }
  }

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
