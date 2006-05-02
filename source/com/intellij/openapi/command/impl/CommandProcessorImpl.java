package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DummyComplexUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.PostprocessReformatingAspect;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 */
public class CommandProcessorImpl extends CommandProcessorEx implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.CommandProcessorImpl");

  private static class CommandDescriptor {
    public Runnable myCommand;
    public Project myProject;
    public String myName;
    public Object myGroupId;
    public UndoConfirmationPolicy myUndoConfirmationPolicy;

    public CommandDescriptor(Runnable command,
                             Project project,
                             String name,
                             Object groupId,
                             UndoConfirmationPolicy undoConfirmationPolicy) {
      myCommand = command;
      myProject = project;
      myName = name;
      myGroupId = groupId;
      myUndoConfirmationPolicy = undoConfirmationPolicy;
    }
  }

  private CommandDescriptor myCurrentCommand = null;
  private Stack<CommandDescriptor> myInterruptedCommands = new Stack<CommandDescriptor>();

//  private HashMap myStatisticsMap = new HashMap(); // command name --> count

  private CopyOnWriteArrayList<CommandListener> myListeners = new CopyOnWriteArrayList<CommandListener>();

  private int myUndoTransparentCount = 0;

  public CommandProcessorImpl() {
    /*
    if (Diagnostic.STATISTICS_LOG_TRACE_ENABLED){
      Application.getInstance().addApplicationListener(
        new ApplicationAdapter(){
          public void applicationExiting(ApplicationEvent event){
            Util.writeStatisticsLog(myStatisticsMap);
          }
        }
      );
    }
    */
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void executeCommand(Runnable runnable, String name, Object groupId) {
    executeCommand(null, runnable, name, groupId);
  }

  public void executeCommand(Project project, Runnable runnable, String name, Object groupId) {
    executeCommand(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT);
  }

  public void executeCommand(Project project,
                             final Runnable command,
                             final String name,
                             final Object groupId,
                             UndoConfirmationPolicy undoConfirmationPolicy) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (LOG.isDebugEnabled()) {
      LOG.debug("executeCommand: " + command + ", name = " + name + ", groupId = " + groupId);
    }
    if (myCurrentCommand != null) {
      command.run();
      return;
    }
    try {
      myCurrentCommand = new CommandDescriptor(command, project, name, groupId, undoConfirmationPolicy);
      fireCommandStarted();
      command.run();
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      _fireCommandFinished();
    }
  }

  private void _fireCommandFinished() {
    try {
      fireBeforeCommandFinished();
      fireCommandFinished();
    }
    finally {
      myCurrentCommand = null;
    }
  }

  public void enterModal() {
    myInterruptedCommands.push(myCurrentCommand);
    if (myCurrentCommand != null) {
      _fireCommandFinished();
    }
  }

  public void leaveModal() {
    LOG.assertTrue(myCurrentCommand == null);
    myCurrentCommand = myInterruptedCommands.pop();
    if (myCurrentCommand != null) {
      fireCommandStarted();
    }
  }

  public void setCurrentCommandName(String name) {
    LOG.assertTrue(myCurrentCommand != null);
    myCurrentCommand.myName = name;
  }

  public void setCurrentCommandGroupId(Object groupId) {
    LOG.assertTrue(myCurrentCommand != null);
    myCurrentCommand.myGroupId = groupId;
  }

  @Nullable
  public Runnable getCurrentCommand() {
    return myCurrentCommand != null ? myCurrentCommand.myCommand : null;
  }

  @Nullable
  public String getCurrentCommandName() {
    if (myCurrentCommand != null) return myCurrentCommand.myName;
    if (myInterruptedCommands.size() > 0) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myName : null;
    }
    return null;
  }

  @Nullable
  public Object getCurrentCommandGroupId() {
    if (myCurrentCommand != null) return myCurrentCommand.myGroupId;
    if (myInterruptedCommands.size() > 0) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myGroupId : null;
    }
    return null;
  }

  @Nullable
  public Project getCurrentCommandProject() {
    return myCurrentCommand != null ? myCurrentCommand.myProject : null;
  }

  public void addCommandListener(CommandListener listener) {
    myListeners.add(listener);
  }

  public void removeCommandListener(CommandListener listener) {
    myListeners.remove(listener);
  }

  public void runUndoTransparentAction(Runnable action) {
    if (myUndoTransparentCount == 0) fireUndoTransparentStarted();
    myUndoTransparentCount++;
    try {
      action.run();
    }
    finally {
      myUndoTransparentCount--;
      if (myUndoTransparentCount == 0) fireUndoTransparentFinished();
    }
  }

  public boolean isUndoTransparentActionInProgress() {
    return myUndoTransparentCount > 0;
  }

  public void markCurrentCommandAsComplex(Project project) {
    UndoManager manager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
    manager.undoableActionPerformed(new DummyComplexUndoableAction());
  }

  private void fireCommandStarted() {
    CommandEvent event = new CommandEvent(this, myCurrentCommand.myCommand, myCurrentCommand.myProject, myCurrentCommand.myUndoConfirmationPolicy);
    for (CommandListener listener : myListeners) {
      try {
        listener.commandStarted(event);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void fireBeforeCommandFinished() {
    CommandEvent event = new CommandEvent(this, myCurrentCommand.myCommand, myCurrentCommand.myName,
                                          myCurrentCommand.myGroupId, myCurrentCommand.myProject, myCurrentCommand.myUndoConfirmationPolicy);
    for (CommandListener listener : myListeners) {
      try {
        listener.beforeCommandFinished(event);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void fireCommandFinished() {
    CommandEvent event = new CommandEvent(this, myCurrentCommand.myCommand, myCurrentCommand.myName,
                                          myCurrentCommand.myGroupId, myCurrentCommand.myProject, myCurrentCommand.myUndoConfirmationPolicy);
    for (CommandListener listener : myListeners) {
      try {
        listener.commandFinished(event);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentStarted() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionStarted();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentFinished() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionFinished();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public String getComponentName() {
    return "CommandProcessor";
  }
}
