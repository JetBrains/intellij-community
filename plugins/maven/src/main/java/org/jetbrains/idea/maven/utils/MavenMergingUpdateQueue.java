/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
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

public class MavenMergingUpdateQueue extends MergingUpdateQueue {

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
    if (ApplicationManager.getApplication().isUnitTestMode()) {
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
    AccessToken accessToken = ReadAction.start();

    try {
      EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();

      multicaster.addCaretListener(new CaretAdapter() {
          @Override
          public void caretPositionChanged(CaretEvent e) {
            MavenMergingUpdateQueue.this.restartTimer();
          }
        }, this);

      multicaster.addDocumentListener(new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent event) {
            MavenMergingUpdateQueue.this.restartTimer();
          }
        }, this);

      project.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        int beforeCalled;

        @Override
        public void beforeRootsChange(ModuleRootEvent event) {
          if (beforeCalled++ == 0) {
            suspend();
          }
        }

        @Override
        public void rootsChanged(ModuleRootEvent event) {
          if (beforeCalled == 0)
            return; // This may occur if listener has been added between beforeRootsChange() and rootsChanged() calls.

          if (--beforeCalled == 0) {
            resume();
            MavenMergingUpdateQueue.this.restartTimer();
          }
        }
      });
    }
    finally {
      accessToken.finish();
    }
  }

  public void makeDumbAware(final Project project) {
    AccessToken accessToken = ReadAction.start();

    try {
      if (DumbService.isDumb(project)) {
        mySuspendCounter.incrementAndGet();
      }
      MessageBusConnection connection = project.getMessageBus().connect(this);
      connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
          suspend();
        }

        @Override
        public void exitDumbMode() {
          resume();
        }
      });

      if (DumbService.getInstance(project).isDumb()) {
        suspend();
      }
    }
    finally {
      accessToken.finish();
    }
  }

  public void makeModalAware(Project project) {
    MavenUtil.invokeLater(project, () -> {
      final ModalityStateListener listener = new ModalityStateListener() {
        @Override
        public void beforeModalityStateChanged(boolean entering) {
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
