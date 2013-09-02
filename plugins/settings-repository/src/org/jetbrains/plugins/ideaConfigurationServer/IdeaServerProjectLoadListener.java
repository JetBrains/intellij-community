package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import org.jetbrains.annotations.NotNull;

final class IdeaServerProjectLoadListener implements ApplicationComponent, SettingsSavingComponent, Disposable {
  @Override
  @NotNull
  public String getComponentName() {
    return "IdeaServerProjectLoadListener";
  }

  @Override
  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
        @Override
        public void beforeProjectLoaded(@NotNull Project project) {
          if (!project.isDefault()) {
            IdeaConfigurationServerManager.getInstance().registerProjectLevelProviders(project);
          }
        }
      });
    }

    IdeaConfigurationServerManager.getInstance().startPing();
  }

  @Override
  public void disposeComponent() {
    IdeaConfigurationServerManager.getInstance().stopPing();
  }

  @Override
  public void save() {
    IdeaConfigurationServerManager.getInstance().getIdeaServerSettings().save();
  }

  @Override
  public void dispose() {
  }
}