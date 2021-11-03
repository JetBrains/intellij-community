// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MavenMergingUpdateQueue extends MergingUpdateQueue {
  private static final Logger LOG = Logger.getInstance(MavenMergingUpdateQueue.class);

  private final AtomicInteger mySuspendCounter = new AtomicInteger(0);

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
    super(name, mergingTimeSpan, isActive, modalityStateComponent, parent, null, false);
  }

  @Override
  public void queue(@NotNull Update update) {
    boolean passThrough = false;
    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      passThrough = isPassThrough();
    }
    else if (MavenUtil.isNoBackgroundMode()) {
      passThrough = true;
    }

    if (passThrough) {
      update.run();
      return;
    }
    super.queue(update);
  }
  
  public void makeUserAware(final Project project) {
    ApplicationManager.getApplication().runReadAction(() -> {
      EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();

      multicaster.addCaretListener(new CaretListener() {
        @Override
        public void caretPositionChanged(@NotNull CaretEvent e) {
          restartTimer();
        }
      }, this);

      multicaster.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          restartTimer();
        }
      }, this);

      project.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        int beforeCalled;

        @Override
        public void beforeRootsChange(@NotNull ModuleRootEvent event) {
          if (beforeCalled++ == 0) {
            suspend();
          }
        }

        @Override
        public void rootsChanged(@NotNull ModuleRootEvent event) {
          if (beforeCalled == 0) {
            return; // This may occur if listener has been added between beforeRootsChange() and rootsChanged() calls.
          }

          if (--beforeCalled == 0) {
            resume();
            restartTimer();
          }
        }
      });
    });
  }

  public void makeModalAware(Project project) {
    MavenUtil.invokeLater(project, () -> {
      final ModalityStateListener listener = new ModalityStateListener() {
        @Override
        public void beforeModalityStateChanged(boolean entering, @NotNull Object modalEntity) {
          if (entering) {
            suspend();
          }
          else {
            resume();
          }
        }
      };
      LaterInvocator.addModalityStateListener(listener, this);
      if (MavenUtil.isInModalContext()) {
        suspend();
      }
    });
  }

  @Override
  public void suspend() {
    if (mySuspendCounter.incrementAndGet() == 1) {
      super.suspend();
    }
  }

  @Override
  public void resume() {
    int c = mySuspendCounter.decrementAndGet();
    if (c <= 0) {
      if (c < 0) {
        mySuspendCounter.set(0);
        LOG.error("Invalid suspend counter state", new Exception());
      }

      super.resume();
    }
  }
}
