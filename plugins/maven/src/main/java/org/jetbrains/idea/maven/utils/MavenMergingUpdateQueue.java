package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenMergingUpdateQueue extends MergingUpdateQueue {
  private final SuspendHelper mySuppendHelper = new SuspendHelper();

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
  public void queue(Update update) {
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
    new ReadAction() {
      protected void run(Result result) throws Throwable {
        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();

        multicaster.addCaretListener(new CaretListener() {
          public void caretPositionChanged(CaretEvent e) {
            MavenMergingUpdateQueue.this.restartTimer();
          }
        }, MavenMergingUpdateQueue.this);

        multicaster.addDocumentListener(new DocumentAdapter() {
          public void documentChanged(DocumentEvent event) {
            MavenMergingUpdateQueue.this.restartTimer();
          }
        }, MavenMergingUpdateQueue.this);

        if (CommandProcessor.getInstance().getCurrentCommand() != null) {
          mySuppendHelper.suspend();
        }

        ProjectRootManager.getInstance(project).addModuleRootListener(new ModuleRootListener() {
          public void beforeRootsChange(ModuleRootEvent event) {
            mySuppendHelper.suspend();
          }

          public void rootsChanged(ModuleRootEvent event) {
            mySuppendHelper.resume();
            MavenMergingUpdateQueue.this.restartTimer();
          }
        }, MavenMergingUpdateQueue.this);
      }
    }.execute();
  }

  public void makeDumbAware(final Project project) {
    new ReadAction() {
      protected void run(Result result) throws Throwable {
        MessageBusConnection connection = project.getMessageBus().connect(MavenMergingUpdateQueue.this);
        connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {

          public void enteredDumbMode() {
            mySuppendHelper.suspend();
          }

          public void exitDumbMode() {
            mySuppendHelper.resume();
          }
        });
        if (DumbService.getInstance(project).isDumb()) {
          mySuppendHelper.suspend();
        }
      }
    }.execute();
  }

  public void makeModalAware(Project project) {
    MavenUtil.invokeAndWait(project, new Runnable() {
      public void run() {
        final ModalityStateListener listener = new ModalityStateListener() {
          public void beforeModalityStateChanged(boolean entering) {
            if (entering) {
              mySuppendHelper.suspend();
            }
            else {
              mySuppendHelper.resume();
            }
          }
        };
        LaterInvocator.addModalityStateListener(listener);
        if (MavenUtil.isInModalContext()) {
          mySuppendHelper.suspend();
        }
        Disposer.register(MavenMergingUpdateQueue.this, new Disposable() {
          public void dispose() {
            LaterInvocator.removeModalityStateListener(listener);
          }
        });
      }
    });
  }

  private class SuspendHelper {
    private final AtomicInteger myCounter = new AtomicInteger(0);

    public void suspend() {
      assert myCounter.get() >= 0;
      if (myCounter.incrementAndGet() == 1) {
        MavenMergingUpdateQueue.this.suspend();
      }
    }

    public void resume() {
      assert myCounter.get() > 0;
      if (myCounter.decrementAndGet() == 0) {
        MavenMergingUpdateQueue.this.resume();
      }
    }
  }
}
