package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "IpnbSettings")
public class IpnbSettings implements PersistentStateComponent<IpnbSettings> {
  public static final String DEFAULT_URL = "http://127.0.0.1:8888";
  public String URL = DEFAULT_URL;
  private String myWorkingDirectory;

  public static IpnbSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, IpnbSettings.class);
  }

  public void setURL(@NotNull final String url) {
    URL = url;
  }

  @Transient
  public String getURL() {
    return URL;
  }

  public void setWorkingDirectory(@Nullable final String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  @Nullable
  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  @Override
  public IpnbSettings getState() {
    return this;
  }

  @Override
  public void loadState(IpnbSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
