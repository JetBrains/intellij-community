// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.execution.process.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * implementation of maven console which sends data to build tool window.
 */
public class BuildToolWindowMavenConsole extends MavenConsole {
  private final MavenBuildEventProcessor myEventParser;

  public BuildToolWindowMavenConsole(@NotNull Project project, @NotNull String title, @NotNull String workingDir) {
    super(getSettings(project).getOutputLevel(), getSettings(project).isPrintErrorStackTraces());

    myEventParser = new MavenBuildEventProcessor(project, title, workingDir);
  }

  private static MavenGeneralSettings getSettings(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings();
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public void setOutputPaused(boolean outputPaused) {

  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(myEventParser));
  }

  @Override
  public void finish() {
    myEventParser.finish();
  }

  @Override
  public void printException(Throwable throwable) {
    super.printException(throwable);
    myEventParser.notifyException(throwable);
  }

  @Override
  protected void doPrint(String text, OutputType type) {
    myEventParser.onTextAvailable(text, type == OutputType.ERROR);
  }

  /*
  public static TextConsoleBuilder createConsoleBuilder(Project project) {

    return new TextConsoleBuilder() {
      private final BuildViewManager myBuildViewManager = ServiceManager.getService(project, BuildViewManager.class);
      private final List<Filter> myFilters = new SmartList<>();

      @NotNull
      @Override
      public ConsoleView getConsole() {
          return new BuildView(project, new DefaultBuildDescriptor(), "build.toolwindow.run.selection.state" , myBuildViewManager);

      }

      @Override
      public void addFilter(@NotNull Filter filter) {
        myFilters.add(filter);

      }

      @Override
      public void setViewer(boolean isViewer) {

      }
    };
  }*/
}
