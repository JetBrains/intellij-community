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

import java.util.ArrayList;
import java.util.Stack;

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

  private ArrayList<CommandListener> myListeners = new ArrayList<CommandListener>();

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
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

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

  public Runnable getCurrentCommand() {
    return myCurrentCommand != null ? myCurrentCommand.myCommand : null;
  }

  public String getCurrentCommandName() {
    if (myCurrentCommand != null) return myCurrentCommand.myName;
    if (myInterruptedCommands.size() > 0) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myName : null;
    }
    return null;
  }

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
    CommandListener[] listeners = myListeners.toArray(new CommandListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      CommandListener listener = listeners[i];
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
    CommandListener[] listeners = myListeners.toArray(new CommandListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      CommandListener listener = listeners[i];
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
    CommandListener[] listeners = myListeners.toArray(new CommandListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      CommandListener listener = listeners[i];
      try {
        listener.commandFinished(event);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentStarted() {
    CommandListener[] listeners = myListeners.toArray(new CommandListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      CommandListener listener = listeners[i];
      try {
        listener.undoTransparentActionStarted();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentFinished() {
    CommandListener[] listeners = myListeners.toArray(new CommandListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      CommandListener listener = listeners[i];
      try {
        listener.undoTransparentActionFinished();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public String getComponentName() {
    return "CommandProcessor";
  }
}
