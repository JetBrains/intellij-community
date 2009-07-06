package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.XSourcePosition;
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
  private final XSourcePosition mySourcePosition;
  private int myHistoryIndex;

  protected XDebuggerEditorBase(final Project project, XDebuggerEditorsProvider debuggerEditorsProvider, @Nullable @NonNls String historyId,
                                final @Nullable XSourcePosition sourcePosition) {
    myProject = project;
    myDebuggerEditorsProvider = debuggerEditorsProvider;
    myHistoryId = historyId;
    mySourcePosition = sourcePosition;
  }

  public abstract JComponent getComponent();

  protected abstract void doSetText(String text);

  public void setText(String text) {
    saveTextInHistory(text);
    doSetText(text);
  }

  public abstract String getText();

  @Nullable
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

  private void saveTextInHistory(final String text) {
    if (myHistoryId != null) {
      XDebuggerHistoryManager.getInstance(myProject).addRecentExpression(myHistoryId, text);
      myHistoryIndex = 0;
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
    return getEditorsProvider().createDocument(getProject(), text, mySourcePosition);
  }

  public boolean canGoBackward() {
    return myHistoryIndex < getRecentExpressions().size()-1;
  }

  public boolean canGoForward() {
    return myHistoryIndex > 0;
  }

  public void goBackward() {
    final List<String> expressions = getRecentExpressions();
    if (myHistoryIndex < expressions.size() - 1) {
      myHistoryIndex++;
      doSetText(expressions.get(myHistoryIndex));
    }
  }

  public void goForward() {
    final List<String> expressions = getRecentExpressions();
    if (myHistoryIndex > 0) {
      myHistoryIndex--;
      doSetText(expressions.get(myHistoryIndex));
    }
  }
}
