package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import org.jetbrains.annotations.NotNull;

final class IcsApplicationLoadListener implements ApplicationLoadListener {
  @Override
  public void beforeApplicationLoaded(Application application) {
    if (application.isUnitTestMode()) {
      return;
    }

    IcsManager.getInstance().registerApplicationLevelProviders(application);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
      @Override
      public void beforeProjectLoaded(@NotNull Project project) {
        if (!project.isDefault()) {
          IcsManager.getInstance().registerProjectLevelProviders(project);
        }
      }
    });
  }
}