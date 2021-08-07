// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.UserActivityListener;
import com.intellij.util.Alarm;

public class CommitablePanelUserActivityListener implements UserActivityListener, Disposable {
  private final Committable myPanel;
  private final Project myProject;
  private final Alarm myAlarm = new Alarm();
  private boolean myApplying;

  public CommitablePanelUserActivityListener(Project project) {
    this(null, project);
  }

  public CommitablePanelUserActivityListener(final Committable panel, Project project) {
    myPanel = panel;
    myProject = project;
  }

  @Override
  final public void stateChanged() {
    if (myApplying) return;
    cancel();
    cancelAllRequests();
    myAlarm.addRequest(() -> {
      myApplying = true;
      cancel();
      try {
        applyChanges();
      }
      finally {
        myApplying = false;
      }
    }, 717);
  }

  private static void cancel() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.cancel();
    }
  }

  protected void applyChanges() {
    if (myPanel != null) {
      commit(myPanel);
    }
  }

  protected final void commit(final Committable panel) {
    getProject().getService(CommittableUtil.class).commit(panel);
  }

  protected final Project getProject() {
    return myProject;
  }

  public final boolean isWaiting() {
    return !myAlarm.isEmpty();
  }

  public final void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }

  @Override
  public void dispose() {
    cancelAllRequests();
  }
}
