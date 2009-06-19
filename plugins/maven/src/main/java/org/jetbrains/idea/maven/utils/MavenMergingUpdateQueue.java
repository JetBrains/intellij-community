package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenMergingUpdateQueue extends MergingUpdateQueue {
  private SuspendHelper mySuppendHelper = new SuspendHelper();

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
    if (isPassThrough() && ApplicationManager.getApplication().isUnitTestMode()) {
      update.run();
      return;
    }
    super.queue(update);
  }

  public void makeUserAware(Project project) {
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();

    multicaster.addCaretListener(new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        MavenMergingUpdateQueue.this.restartTimer();
      }
    }, this);

    multicaster.addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent event) {
        MavenMergingUpdateQueue.this.restartTimer();
      }
    }, this);

    CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {
      @Override
      public void commandStarted(CommandEvent event) {
        mySuppendHelper.suspend();
      }

      @Override
      public void commandFinished(CommandEvent event) {
        mySuppendHelper.resume();
      }
    }, this);

    ProjectRootManager.getInstance(project).addModuleRootListener(new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
        mySuppendHelper.suspend();
      }

      public void rootsChanged(ModuleRootEvent event) {
        mySuppendHelper.resume();
      }
    }, this);
  }

  public void makeDumbAware(Project project) {
    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      public void beforeEnteringDumbMode() {
        mySuppendHelper.resume();
      }

      public void enteredDumbMode() {
      }

      public void exitDumbMode() {
        mySuppendHelper.resume();
      }
    });
  }

  private class SuspendHelper {
    private final AtomicInteger myCounter = new AtomicInteger(0);

    public void suspend() {
      if (myCounter.incrementAndGet() == 1) {
        MavenMergingUpdateQueue.this.suspend();
      }
    }

    public void resume() {
      if (myCounter.decrementAndGet() == 0) {
        MavenMergingUpdateQueue.this.resume();
      }
    }
  }

}
