package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import org.jetbrains.annotations.NotNull;

final class IcsProjectLoadListener implements ApplicationComponent, SettingsSavingComponent {
  @Override
  @NotNull
  public String getComponentName() {
    return "IcsProjectLoadListener";
  }

  @Override
  public void initComponent() {
    IcsManager.getInstance().startPing();
  }

  @Override
  public void disposeComponent() {
    IcsManager.getInstance().stopPing();
  }

  @Override
  public void save() {
    IcsManager.getInstance().getIdeaServerSettings().save();
  }
}