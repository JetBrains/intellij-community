package com.jetbrains.edu.learning.twitter;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.StudyTwitterPluginConfigurator;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

/**
 * Action that provide tweeting functionality to plugin.
 * Is performed for every solved task and configured by StudyPluginConfigurator instance.
 * 
 * In order to provide tweeting functionality in your plugin you should override twitter 
 * methods in StudyPluginConfigurator instance of your plugin.
 */
public class StudyTwitterAction extends StudyAfterCheckAction {
  private StudyTwitterPluginConfigurator myConfigurator;

  public StudyTwitterAction(@NotNull final StudyTwitterPluginConfigurator configurator) {
    myConfigurator = configurator;
  }

  @Override
  public void run(@NotNull Project project, @NotNull Task solvedTask, StudyStatus statusBeforeCheck) {

    if (myConfigurator.askToTweet(project, solvedTask, statusBeforeCheck)) {
      StudyTwitterUtils.createTwitterDialogAndShow(project, myConfigurator, solvedTask);
    }
  }
}
