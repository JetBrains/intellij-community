package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.ui.impl.WatchPanel;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 4:30:11 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class EvaluationDialog extends DialogWrapper {
  private final MyEvaluationPanel myEvaluationPanel;
  private final Project myProject;
  private final DebuggerContextListener myContextListener;
  private final DebuggerEditorImpl myEditor;
  private final List<Runnable> myDisposeRunnables = new ArrayList<Runnable>();

  public EvaluationDialog(Project project, TextWithImports text) {
    super(project, true);
    myProject = project;
    setModal(false);
    setCancelButtonText("Close");
    setOKButtonText("Evaluate");

    myEditor   = createEditor();
    myEvaluationPanel = new MyEvaluationPanel(myProject);

    setDebuggerContext(getDebuggerContext());
    initDialogData(text);

    myContextListener = new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        boolean close = true;
        for (Iterator iterator = DebuggerManagerEx.getInstanceEx(myProject).getSessions().iterator(); iterator.hasNext();) {
          DebuggerSession session = (DebuggerSession) iterator.next();
          if(!session.isStopped()) {
            close = false;
            break;
          }
        }

        if(close) {
          close(CANCEL_EXIT_CODE);
        }
        else {
          setDebuggerContext(newContext);
        }
      }
    };
    DebuggerManagerEx.getInstanceEx(myProject).getContextManager().addListener(myContextListener);

    setHorizontalStretch(1f);
    setVerticalStretch(1f);
  }

  protected void doOKAction() {
    if (isOKActionEnabled()) {
      doEvaluate();
    }
  }

  protected void doEvaluate() {
    if (myEditor == null || myEvaluationPanel == null) {
      return;
    }

    myEvaluationPanel.clear();
    TextWithImports codeToEvaluate = getCodeToEvaluate();
    if (codeToEvaluate == null) {
      return;
    }
    try {
      setOKActionEnabled(false);
      NodeDescriptorImpl descriptor = myEvaluationPanel.getWatchTree().addWatch(codeToEvaluate).getDescriptor();
      myEvaluationPanel.getWatchTree().rebuild(getDebuggerContext());
      descriptor.myIsExpanded = true;
    }
    finally {
      setOKActionEnabled(true);
    }
    getEditor().addRecent(getCodeToEvaluate());
  }

  protected TextWithImports getCodeToEvaluate() {
    TextWithImports text = getEditor().getText();
    String s = text.getText();
    if (s != null) {
      s = s.trim();
    }
    if ("".equals(s)) {
      return null;
    }
    return text;
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getPreferredFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.ui.EvaluationDialog2";
  }

  protected void addDisposeRunnable (Runnable runnable) {
    myDisposeRunnables.add(runnable);
  }

  protected void dispose() {
    for (Runnable runnable : myDisposeRunnables) {
      runnable.run();
    }
    myDisposeRunnables.clear();
    myEditor.dispose();
    DebuggerManagerEx.getInstanceEx(myProject).getContextManager().removeListener(myContextListener);
    myEvaluationPanel.dispose();
    super.dispose();
  }

  protected class MyEvaluationPanel extends WatchPanel {
    public MyEvaluationPanel(final Project project) {
      super(project, (DebuggerManagerEx.getInstanceEx(project)).getContextManager());
      getWatchTree().setEvaluationPriority(DebuggerManagerThreadImpl.HIGH_PRIORITY);
      getWatchTree().setAllowBreakpoints(true);
    }

    protected ActionPopupMenu createPopupMenu() {
      ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.EVALUATION_DIALOG_POPUP);
      return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.EVALUATION_DIALOG_POPUP, group);
    }

    protected void changeEvent(DebuggerContextImpl newContext, int event) {
      if(event == DebuggerSession.EVENT_REFRESH) {
        return;
      }
      final DebuggerSession debuggerSession = newContext.getDebuggerSession();
      if(debuggerSession != null && debuggerSession.getState() == DebuggerSession.STATE_WAIT_EVALUATION) {
        return;
      }

      super.changeEvent(newContext, event);
    }
  }

  protected void setDebuggerContext(DebuggerContextImpl context) {
    myEditor.setContext(PositionUtil.getContextElement(context));
  }

  protected PsiElement getContext() {
    return myEditor.getContext();
  }

  protected void initDialogData(TextWithImports text) {
    getEditor().setText(text);
    myEvaluationPanel.clear();
  }

  public DebuggerContextImpl getDebuggerContext() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContext();
  }

  public DebuggerEditorImpl getEditor() {
    return myEditor;
  }

  protected abstract DebuggerEditorImpl createEditor();

  protected MyEvaluationPanel getEvaluationPanel() {
    return myEvaluationPanel;
  }

  public Project getProject() {
    return myProject;
  }

}
