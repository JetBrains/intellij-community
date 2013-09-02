package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.ultimate.PluginVerifier;
import com.intellij.ultimate.UltimateVerifier;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;


@SuppressWarnings({"HardCodedStringLiteral"})
public class IdeaServerProjectLoadListener implements ApplicationComponent, SettingsSavingComponent {

  public IdeaServerProjectLoadListener(UltimateVerifier verifier) {
    PluginVerifier.verifyUltimatePlugin(verifier);
  }

  private MessageBusConnection myMessageBusConnection;

  @NotNull
  public String getComponentName() {
    return "IdeaServerProjectLoadListener";
  }

  public void initComponent() {
    myMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myMessageBusConnection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
        public void beforeProjectLoaded(@NotNull final Project project) {
          if (!project.isDefault()) {
            IdeaConfigurationServerManager.getInstance().registerProjectLevelProviders(project);
          }
        }
      });
    }

    IdeaConfigurationServerManager.getInstance().startPing();
  }

  public void disposeComponent() {
    IdeaConfigurationServerManager.getInstance().stopPing();
    myMessageBusConnection.disconnect();
  }

  public void save() {
    IdeaConfigurationServerManager.getInstance().getIdeaServerSettings().save();
  }
}
