package org.jetbrains.idea.maven.performancePlugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.DisposeAwareRunnable;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.importing.FilesList;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

public final class ImportMavenProjectCommand extends AbstractCommand {
  public static final String PREFIX = "%importMavenProject";

  public ImportMavenProjectCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {

    @NotNull Project project = context.getProject();
    if (MavenUtil.isLinearImportEnabled()) {
      return runLinearMavenImport(context, project);
    }
    else {
      ActionCallback actionCallback = new ActionCallbackProfilerStopper();
      runWhenMavenImportAndIndexingFinished(context, () -> actionCallback.setDone(), project);
      return Promises.toPromise(actionCallback);
    }
  }

  private Promise<Object> runLinearMavenImport(PlaybackContext context, Project project) {
    ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project);
    ExternalSystemProjectTrackerSettings.AutoReloadType currentAutoReloadType = projectTrackerSettings.getAutoReloadType();
    projectTrackerSettings.setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.NONE);

    context.message("Waiting for fully open and initialized maven project", getLine());
    context.message("Import of the project has been started", getLine());
    AsyncPromise<Object> result = new AsyncPromise<>();
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized(() -> {
      MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
      MavenImportingManager.getInstance(project).openProjectAndImport(
        new FilesList(mavenManager.collectAllAvailablePomFiles())
      ).getFinishPromise().onSuccess(t -> {
        context.message("Import of the maven project has been finished", getLine());
        projectTrackerSettings.setAutoReloadType(currentAutoReloadType);
        DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(() -> result.setResult(t), project));
      }).onError(t -> result.setError(t));
    });
    return result;
  }

  private void runWhenMavenImportAndIndexingFinished(@NotNull PlaybackContext context,
                                                     @NotNull Runnable runnable,
                                                     @NotNull Project project) {
    ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project);
    ExternalSystemProjectTrackerSettings.AutoReloadType currentAutoReloadType = projectTrackerSettings.getAutoReloadType();
    projectTrackerSettings.setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.NONE);

    context.message("Waiting for fully open and initialized maven project", getLine());

    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized(() -> MavenUtil.runWhenInitialized(project, () -> {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        waitForCurrentMavenImportActivities(context, project)
          .thenAsync(promise -> {
            context.message("Import of the project has been started", getLine());
            MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
            if (!mavenManager.isMavenizedProject()) {
              mavenManager.addManagedFiles(mavenManager.collectAllAvailablePomFiles());
            }
            return mavenManager.forceUpdateProjects();
          })
          .thenAsync(promise -> waitForCurrentMavenImportActivities(context, project))
          .onProcessed(promise -> {
            context.message("Import of the maven project has been finished", getLine());
            projectTrackerSettings.setAutoReloadType(currentAutoReloadType);
            DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(runnable, project));
          });
      });
    }));
  }

  private Promise<?> waitForCurrentMavenImportActivities(@NotNull PlaybackContext context, @NotNull Project project) {
    context.message("Waiting for current maven import activities", getLine());
    return MavenProjectsManager.getInstance(project).waitForImportCompletion().onProcessed(o -> {
      context.message("Maven import activities completed", getLine());
    });
  }
}