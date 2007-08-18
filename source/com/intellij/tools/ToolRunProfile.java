/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.tools;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2005
 */
public class ToolRunProfile implements RunProfile{
  private final Tool myTool;
  private final GeneralCommandLine myCommandLine;

  public ToolRunProfile(final Tool tool, final DataContext context) {
    myTool = tool;
    myCommandLine = myTool.createCommandLine(context);
    if (context instanceof DataManagerImpl.MyDataContext) {
      // hack: macro.expand() can cause UI events such as showing dialogs ('Prompt' macro) which may 'invalidate' the datacontext
      // since we know exactly that context is valid, we need to update its event count
      ((DataManagerImpl.MyDataContext)context).setEventCount(IdeEventQueue.getInstance().getEventCount());
    }
  }

  public String getName() {
    return myTool.getName();
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
  }

  public Module[] getModules() {
    return null;
  }

  public RunProfileState getState(DataContext context, RunnerInfo runnerInfo, RunnerSettings runnerSettings, ConfigurationPerRunnerSettings configurationSettings) {
    final Project project = DataKeys.PROJECT.getData(context);
    if (project == null || myCommandLine == null) {
      // can return null if creation of cmd line has been cancelled
      return null;
    }

    final CommandLineState commandLineState = new CommandLineState(runnerSettings, configurationSettings) {
      protected GeneralCommandLine createCommandLine() {
        return myCommandLine;
      }

      public ExecutionResult execute() throws ExecutionException {
        final ExecutionResult result = super.execute();
        final ProcessHandler processHandler = result.getProcessHandler();
        if (processHandler != null) {
          processHandler.addProcessListener(new ToolProcessAdapter(project, myTool.synchronizeAfterExecution(), getName()));
        }
        return result;
      }
    };
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    final FilterInfo[] outputFilters = myTool.getOutputFilters();
    for (int i = 0; i < outputFilters.length; i++) {
      builder.addFilter(new RegexpFilter(project, outputFilters[i].getRegExp()));
    }

    commandLineState.setConsoleBuilder(builder);
    return commandLineState;
  }

}
