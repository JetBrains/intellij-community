package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: anna
 * Date: 01-Feb-2006
 */
public class LogFilesManager {

  private Map<LogFileOptions, Set<String>> myLogFileManagerMap = new HashMap<LogFileOptions, Set<String>>();
  private Map<LogFileOptions, RunConfigurationBase> myLogFileToConfiguration = new HashMap<LogFileOptions, RunConfigurationBase>();
  private Runnable myUpdateRequest;
  private LogConsoleManager myManager;
  private Alarm myUpdateAlarm = new Alarm();

  public LogFilesManager(final Project project, LogConsoleManager manager) {
    myManager = manager;
    myUpdateRequest = new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        if (myUpdateAlarm == null) return; //already disposed
        myUpdateAlarm.cancelAllRequests();
        for (LogFileOptions logFile : myLogFileManagerMap.keySet()) {
          Set<String> oldFiles = myLogFileManagerMap.get(logFile);
          Set<String> newFiles = logFile.getPaths();
          for (String file : newFiles) {
            if (!oldFiles.contains(file)){
              myManager.addLogConsole(logFile.getName(), file, 0);
            }
          }
          for (String oldFile : oldFiles) {
            if (!newFiles.contains(oldFile)){
              myManager.removeLogConsole(oldFile);
            }
          }
          oldFiles.clear();
          oldFiles.addAll(newFiles);
        }
        myUpdateAlarm.addRequest(myUpdateRequest, 300, ModalityState.NON_MODAL);
      }
    };
  }

  public void registerFileMatcher(final RunConfigurationBase runConfiguration){
    final ArrayList<LogFileOptions> logFiles = runConfiguration.getAllLogFiles();
    for (LogFileOptions logFile : logFiles) {
      if (logFile.isEnabled()) {
        myLogFileManagerMap.put(logFile, logFile.getPaths());
        myLogFileToConfiguration.put(logFile, runConfiguration);
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myUpdateAlarm.addRequest(myUpdateRequest, 300, ModalityState.NON_MODAL);
      }
    });
  }

  public void unregisterFileMatcher(){
    if (myUpdateAlarm != null) {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm = null;
    }
  }

  public void initLogConsoles(RunConfigurationBase base, ProcessHandler startedProcess) {
    final ArrayList<LogFileOptions> logFiles = base.getAllLogFiles();
    for (LogFileOptions logFile : logFiles) {
      if (logFile.isEnabled()) {
        final Set<String> paths = logFile.getPaths();
        for (String path : paths) {
          myManager.addLogConsole(logFile.getName(), path, logFile.isSkipContent() ? new File(path).length() : 0);
        }
      }
    }
    base.createAdditionalTabComponents(myManager, startedProcess);
  }
}
