package com.intellij.debugger.ui;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.ui.impl.WatchPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;

import javax.swing.*;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 4:30:11 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class EvaluationDialog extends DialogWrapper {
  private MyEvaluationPanel myEvaluationPanel;
  private Project myProject;
  private final DebuggerContextListener myContextListener;
  private final DebuggerEditorImpl myEditor;

  //TODO:[lex] move this code to DebuggerEditorImpl
  private final PsiTreeChangeListener myPsiListener = new PsiTreeChangeAdapter() {
      public void childRemoved(PsiTreeChangeEvent event)           { checkContext(); }
      public void childReplaced(PsiTreeChangeEvent event)          { checkContext(); }
      public void childMoved(PsiTreeChangeEvent event)             { checkContext(); }
    };

  private void checkContext() {
    if(getContext() != null) {
      if(!getContext().isValid()) {
        setDebuggerContext(DebuggerManagerEx.getInstanceEx(myProject).getContextManager().getContext());
      }
    }
  }

  public EvaluationDialog(Project project, TextWithImportsImpl text) {
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
        } else {
          setDebuggerContext(newContext);
        }
      }
    };
    DebuggerManagerEx.getInstanceEx(myProject).getContextManager().addListener(myContextListener);

    setHorizontalStretch(1f);
    setVerticalStretch(1f);

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiListener);
  }

  protected void doOKAction() {
    if (isOKActionEnabled()) {
      doEvaluate();
      //getDebuggerContext().getDebuggerSession().refresh();
    }
  }

  protected void doEvaluate() {
    if (myEditor == null || myEvaluationPanel == null) {
      return;
    }

    myEvaluationPanel.clear();
    TextWithImportsImpl codeToEvaluate = getCodeToEvaluate();
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

  protected TextWithImportsImpl getCodeToEvaluate() {
    TextWithImportsImpl text = (TextWithImportsImpl)getEditor().getText();
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

  protected void dispose() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiListener);
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
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(DebuggerActions.EVALUATION_DIALOG_POPUP, group);
      return popupMenu;
    }

    protected void changeEvent(DebuggerContextImpl newContext, int event) {
      if(event == DebuggerSession.EVENT_REFRESH) return;
      if(newContext.getDebuggerSession() != null && newContext.getDebuggerSession().getState() == DebuggerSession.STATE_WAIT_EVALUATION) return;
      
      super.changeEvent(newContext, event);
    }
  }

  protected void setDebuggerContext(DebuggerContextImpl context) {
    myEditor.setContext(PositionUtil.getContextElement(context));
  }

  protected PsiElement getContext() {
    return myEditor.getContext();
  }

  protected void initDialogData(TextWithImportsImpl text) {
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
