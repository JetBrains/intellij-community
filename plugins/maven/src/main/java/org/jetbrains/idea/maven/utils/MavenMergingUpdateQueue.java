package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.*;

public class MavenMergingUpdateQueue extends MergingUpdateQueue {
  public MavenMergingUpdateQueue(String name,
                                 int mergingTimeSpan,
                                 boolean isActive) {
    this(name, mergingTimeSpan, isActive, null);
  }

  public MavenMergingUpdateQueue(String name,
                                 int mergingTimeSpan,
                                 boolean isActive,
                                 Disposable parent) {
    this(name, mergingTimeSpan, isActive, ANY_COMPONENT, parent);
  }

  public MavenMergingUpdateQueue(String name,
                                 int mergingTimeSpan,
                                 boolean isActive,
                                 JComponent modalityStateComponent,
                                 Disposable parent) {
    super(name, mergingTimeSpan, isActive, modalityStateComponent, parent);
  }

  @Override
  public void queue(Update update) {
    if (isPassThrough() && ApplicationManager.getApplication().isUnitTestMode()) {
      update.run();
      return;
    }
    super.queue(update);
  }
}
