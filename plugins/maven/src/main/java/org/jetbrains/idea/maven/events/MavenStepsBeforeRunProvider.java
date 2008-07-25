package org.jetbrains.idea.maven.events;

import com.intellij.execution.StepsBeforeRunProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenStepsBeforeRunProvider implements StepsBeforeRunProvider {
  private final Project myProject;

  public MavenStepsBeforeRunProvider(Project project) {
    myProject = project;
  }

  public String getStepName() {
    return MavenEventsHandler.RUN_MAVEN_STEP;
  }

  public String getStepDescription(final RunConfiguration runConfiguration) {
    return MavenEventsHandler.getInstance(myProject).getRunStepDescription(runConfiguration);
  }

  public boolean hasTask(RunConfiguration configuration) {
    return getEventsHandler().getTask(configuration.getType(), configuration) != null;
  }

  public boolean executeTask(final DataContext context, final RunConfiguration configuration) {
    final boolean[] result = new boolean[]{false};
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        new Task.Modal(PlatformDataKeys.PROJECT.getData(context),
                       EventsBundle.message("execute.before.launch.steps.title"),
                       true) {
          public void run(@NotNull ProgressIndicator indicator) {
            final MavenTask task = getEventsHandler().getTask(configuration.getType(), configuration);
            result[0] = task != null && getEventsHandler().execute(Arrays.asList(task), indicator);
          }
        };
      }
    }, ModalityState.NON_MODAL);
    return result[0];
  }

  public void copyTaskData(final RunConfiguration from, final RunConfiguration to) {
    final MavenTask mavenTask = getEventsHandler().getAssignedTask(from.getType(), from);
    if (mavenTask != null) {
      getEventsHandler().assignTask(to.getType(), to, mavenTask.clone());
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
    return MavenEventsHandler.getInstance(myProject).configureRunStep(runConfiguration);
  }

  private MavenEventsHandler getEventsHandler() {
    return myProject.getComponent(MavenEventsHandler.class);
  }
}
