package com.intellij.debugger.ui;

import com.intellij.Patches;
import com.intellij.util.ui.MessageCategory;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerBundle;
import gnu.trove.TIntObjectHashMap;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class HotSwapProgressImpl extends HotSwapProgress{
  TIntObjectHashMap<List<String>> myMessages = new TIntObjectHashMap<List<String>>();
  private final ProgressIndicator myProgressIndicator;
  private final ProgressWindow myProgressWindow;
  private String myTitle = DebuggerBundle.message("progress.hot.swap.title");

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

  public void finished() {
    super.finished();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final List<String> errors = getMessages(MessageCategory.ERROR);
        final List<String> warnings = getMessages(MessageCategory.WARNING);
        if (errors.size() > 0) {
          Messages.showErrorDialog(getProject(), buildMessage(errors), myTitle);
          WindowManager.getInstance().getStatusBar(getProject()).setInfo(DebuggerBundle.message("status.hot.swap.completed.with.errors"));
        }
        else if (warnings.size() > 0){
          Messages.showWarningDialog(getProject(), buildMessage(warnings), myTitle);
          WindowManager.getInstance().getStatusBar(getProject()).setInfo(DebuggerBundle.message("status.hot.swap.completed.with.warnings"));
        }
        else {
          final StringBuffer msg = new StringBuffer();
          for (int category : myMessages.keys()) {
            if (msg.length() > 0) {
              msg.append("\n");
            }
            msg.append(buildMessage(getMessages(category)));
          }
          WindowManager.getInstance().getStatusBar(getProject()).setInfo(msg.toString());
        }
      }
    });
  }

  private List<String> getMessages(int category) {
    final List<String> messages = myMessages.get(category);
    return messages == null? (List<String>)Collections.EMPTY_LIST : messages;
  }
    
  private String buildMessage(List<String> messages) {
    StringBuffer msg = new StringBuffer();
    for (Iterator<String> it = messages.iterator(); it.hasNext();) {
      final String message = it.next();
      if (msg.length() > 0) {
        msg.append("\n");
      }
      msg.append(message);
    }
    return msg.toString();
  } 
  
  public void addMessage(final int type, final String text) {
    List<String> messages = myMessages.get(type);
    if (messages == null) {
      messages = new ArrayList<String>();
      myMessages.put(type, messages);
    }
    messages.add(text);
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
    myTitle = DebuggerBundle.message("progress.hot.swap.title") + " : " + session.getSessionName();
    myProgressWindow.setTitle(myTitle);
  }
}
