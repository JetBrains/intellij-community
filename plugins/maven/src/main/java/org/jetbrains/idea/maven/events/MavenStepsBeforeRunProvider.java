package org.jetbrains.idea.maven.events;

import com.intellij.execution.StepsBeforeRunProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;

import java.util.Arrays;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenStepsBeforeRunProvider implements StepsBeforeRunProvider {
  private final Project myProject;

  public MavenStepsBeforeRunProvider( Project project) {
    myProject = project;
  }

  public String getStepName() {
    return MavenEventsComponent.RUN_MAVEN_STEP;
  }

  public String getStepDescription(final RunConfiguration runConfiguration) {
    return MavenEventsComponent.getInstance(myProject).getRunStepDescription(runConfiguration);
  }

  public boolean hasTask(RunConfiguration configuration) {
    return getState().getTask(configuration.getType(), configuration.getName()) != null;
  }

  public boolean executeTask(final DataContext context, final RunConfiguration configuration) {
    final boolean [] result = new boolean[]{false};
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            final MavenTask task = getState().getTask(configuration.getType(), configuration.getName());
            result [0] = task != null && getEventsHandler().execute(Arrays.asList(task));
          }
        }, EventsBundle.message("execute.before.launch.steps.title"), true, PlatformDataKeys.PROJECT.getData(context));
      }
    }, ModalityState.NON_MODAL);
    return result[0];
  }

  public void copyTaskData(final RunConfiguration from, final RunConfiguration to) {
    final MavenTask mavenTask = getState().getAssignedTask(from.getType(), from.getName());
    if (mavenTask != null) {
      getState().assignTask(to.getType(), to.getName(), mavenTask.clone()); 
      // no need to update shortcut description actually, as the presentation of mavenTask should not change
    }
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  public boolean hasConfigurationButton() {
    return true;
  }

  public String configureStep(final RunConfiguration runConfiguration) {
    return MavenEventsComponent.getInstance(myProject).configureRunStep(runConfiguration);
  }

  private MavenEventsHandler getEventsHandler() {
    return myProject.getComponent(MavenEventsHandler.class);
  }

  public MavenEventsState getState() {
    return getEventsHandler().getState();
  }
}
