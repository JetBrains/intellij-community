package com.intellij.execution.impl;

import com.intellij.ant.AntConfiguration;
import com.intellij.ant.impl.MapDataContext;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerConfig;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dyoma
 */
public class ExecutionManagerImpl extends ExecutionManager implements ProjectComponent {
  private final Project myProject;

  private RunContentManagerImpl myContentManager;

  /**
   * reflection
   */
  ExecutionManagerImpl(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
    ((RunContentManagerImpl)getContentManager()).init();
  }

  public void projectClosed() {
    myContentManager.dispose();
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public RunContentManager getContentManager() {
    if (myContentManager == null) {
      myContentManager = new RunContentManagerImpl(myProject);
    }
    return myContentManager;
  }

  public ProcessHandler[] getRunningProcesses() {
    final List<ProcessHandler> handlers = new ArrayList<ProcessHandler>();
    RunContentDescriptor[] descriptors = myContentManager.getAllDescriptors();
    for (int q = 0; q < descriptors.length; q++) {
      RunContentDescriptor descriptor = descriptors[q];
      if (descriptor != null) {
        final ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null) {
          handlers.add(processHandler);
        }
      }
    }
    return handlers.toArray(new ProcessHandler[handlers.size()]);
  }

  public void compileAndRun(final Runnable startRunnable,
                            final RunProfile configuration,
                            final RunProfileState state) {
    final Runnable antAwareRunnable = new Runnable() {
      public void run() {
        final AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
        if ((configuration instanceof RunConfiguration) &&
            antConfiguration.hasTasksToExecuteBeforeRun((RunConfiguration)configuration)) {
          final Thread thread = new Thread(new Runnable() {
            public void run() {
              final DataContext dataContext = MapDataContext.singleData(DataConstants.PROJECT, myProject);
              final boolean result = antConfiguration.executeTaskBeforeRun(dataContext, (RunConfiguration)configuration);
              if (result) {
                ApplicationManager.getApplication().invokeLater(startRunnable);
              }
            }
          });
          thread.start();
        }
        else {
          startRunnable.run();
        }
      }
    };
    Module[] modulesToCompile = state.getModulesToCompile();
    if (modulesToCompile == null) modulesToCompile = Module.EMPTY_ARRAY;
    if (getConfig().isCompileBeforeRunning() && modulesToCompile.length > 0) {
      final CompileStatusNotification callback = new CompileStatusNotification() {
        public void finished(final boolean aborted, final int errors, final int warnings) {
          if (errors == 0 && !aborted) {
            ApplicationManager.getApplication().invokeLater(antAwareRunnable);
          }
        }
      };
      if ("true".equals(System.getProperty("makeProjectOnRun", "false"))) {
        // user explicitly requested whole-project make
        CompilerManager.getInstance(myProject).make(callback);
      }
      else {
        if (modulesToCompile.length > 0) {
          CompilerManager.getInstance(myProject).make(myProject, modulesToCompile, callback);
        }
        else {
          CompilerManager.getInstance(myProject).make(callback);
        }
      }
    }
    else {
      antAwareRunnable.run();
    }
  }

  private RunManagerConfig getConfig() {
    return RunManager.getInstance(myProject).getConfig();
  }

  public String getComponentName() {
    return "ExecutionManager";
  }
}
