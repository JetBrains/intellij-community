package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "IpnbSettings")
public class IpnbSettings implements PersistentStateComponent<IpnbSettings> {
  private static final String IPNB_PASSWORD_KEY = "IPNB_SSH_SETTINGS_PASSWORD_KEY";
  public static final String DEFAULT_URL = "http://127.0.0.1:8888";
  public String URL = DEFAULT_URL;
  private String myWorkingDirectory;
  private String myArguments = "";
  private String myUsername;

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

  public String getArguments() {
    return myArguments;
  }

  public void setArguments(String arguments) {
    myArguments = arguments;
  }
  
  @NotNull
  public String getUsername() {
    return StringUtil.notNullize(myUsername);
  }

  public void setUsername(@Nullable String username) {
    myUsername = username;
  }
  
  @Transient
  @NotNull
  public String getPassword() {
    final String username = getUsername();
    if (StringUtil.isEmptyOrSpaces(username)) return "";
    return StringUtil.notNullize(PasswordSafe.getInstance().getPassword(IpnbSettings.class, IPNB_PASSWORD_KEY + username));
  }

  @Transient
  public void setPassword(@NotNull String password) {
    final String username = getUsername();
    if (password.isEmpty() || username.isEmpty()) return;
    PasswordSafe.getInstance().setPassword(IpnbSettings.class, IPNB_PASSWORD_KEY + username, password);
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
