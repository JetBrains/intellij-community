package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.sun.javafx.application.PlatformImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "IpnbSettings")
public class IpnbSettings implements PersistentStateComponent<IpnbSettings> {
  private static final String IPNB_PASSWORD_KEY = "IPNB_SSH_SETTINGS_PASSWORD_KEY";
  private String myUsername;
  private boolean hasFx = true;

  public static IpnbSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, IpnbSettings.class);
  }
  @NotNull
  public String getUsername() {
    return StringUtil.notNullize(myUsername);
  }

  public void setUsername(@Nullable String username) {
    myUsername = username;
  }

  public boolean hasFx() {
    if (hasFx) {
      try {
        PlatformImpl.setImplicitExit(false);
      }
      catch (NoClassDefFoundError e) {
        hasFx = false;
      }
    }
    return hasFx;
  }

  public void setHasFx(boolean hasFx) {
    this.hasFx = hasFx;
  }

  @Transient
  @NotNull
  public String getPassword(@NotNull String projectPathHash) {
  final String username = getUsername();
    final String url = "";
    final String accountName = createAccountName(username, url, projectPathHash);
    final String newStylePassword = PasswordSafe.getInstance().getPassword(IpnbSettings.class, accountName);
    
   return StringUtil.notNullize(newStylePassword == null ? PasswordSafe.getInstance().getPassword(IpnbSettings.class, username) : newStylePassword);
  }

  @Transient
  public void setPassword(@NotNull String password, @NotNull String projectPathHash) {
    final String username = getUsername();
    final String url = "";
    final String accountName = createAccountName(username, url, projectPathHash);
    PasswordSafe.getInstance().setPassword(IpnbSettings.class, accountName, password);
  }

  private static String createAccountName(@NotNull String username, @NotNull String url, @NotNull String projectPath) {
    return IPNB_PASSWORD_KEY + url + username + projectPath;
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
