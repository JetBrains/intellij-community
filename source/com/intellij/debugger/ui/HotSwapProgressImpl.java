package com.intellij.debugger.ui;

import com.intellij.Patches;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.util.IJSwingUtilities;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class HotSwapProgressImpl extends HotSwapProgress{
  private HotSwapView myHotSwapView;
  private final ProgressIndicator myProgressIndicator;
  private final ProgressWindow myProgressWindow;

  private DebuggerSession myDebuggerSession;

  public HotSwapProgressImpl(Project project) {
    super(project);
    myProgressWindow = new ProgressWindow(true, getProject()) {
      public void cancel() {
        HotSwapProgressImpl.this.cancel();
        super.cancel();
        if (isRunning()) {
          stop();
        }
      }
    };
    myProgressIndicator = Patches.MAC_HIDE_QUIT_HACK ? myProgressWindow : (ProgressIndicator)new SmoothProgressAdapter(myProgressWindow, project);
  }

  public void addMessage(final int type, final String[] text, final VirtualFile file, final int line, final int column) {
    IJSwingUtilities.invoke(new Runnable() {
      public void run() {
        if(myHotSwapView == null) {
          myHotSwapView = HotSwapView.findView(myDebuggerSession);          
        }
        myHotSwapView.addMessage(type, text, file, line, column, null);
      }
    });
  }

  public void setText(final String text) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        myProgressIndicator.setText(text);
      }
    }, myProgressIndicator.getModalityState());

  }

  public void setTitle(final String text) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        myProgressWindow.setTitle(text);
      }
    }, myProgressWindow.getModalityState());

  }

  public void setFraction(final double v) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        myProgressIndicator.setFraction(v);
      }
    }, myProgressIndicator.getModalityState());
  }

  public boolean isCancelled() {
    return myProgressIndicator.isCanceled();
  }

  public ProgressIndicator getProgressIndicator() {
     return myProgressIndicator;
  }

  public void setDebuggerSession(DebuggerSession session) {
    final DebuggerSession oldSession = myDebuggerSession;
    myDebuggerSession = session;
    myProgressWindow.setTitle("Hot Swap : " + myDebuggerSession.getSessionName());
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        myHotSwapView = null;
      }
    });
  }
}
