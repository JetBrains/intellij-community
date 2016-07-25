package com.jetbrains.edu.learning.stepic;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.StudyTaskManager;
import org.jetbrains.annotations.NotNull;

public class StepicUser {
  private static final String STEPIC_SETTINGS_PASSWORD_KEY = "STEPIC_SETTINGS_PASSWORD_KEY";
  private static final Logger LOG = Logger.getInstance(StepicUser.class);
  private int id = -1;
  private String firstName = "";
  private String lastName = "";
  private String email = "";
  private String accessToken = "";
  private String refreshToken = "";


  public StepicUser() {
  }
  
  public StepicUser(@NotNull final String email, @NotNull final String password) {
    this.email = email;
    setPassword(password);
  }

  public StepicUser(StepicWrappers.TokenInfo tokenInfo) {
    this.accessToken = tokenInfo.accessToken;
    this.refreshToken = tokenInfo.refreshToken;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  @NotNull
  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(@NotNull final String firstName) {
    this.firstName = firstName;
  }

  @NotNull
  public String getLastName() {
    return lastName;
  }

  public void setLastName(@NotNull final String last_name) {
    this.lastName = last_name;
  }

  @NotNull
  public String getEmail() {
    return email;
  }

  public void setEmail(@NotNull final String email) {
    this.email = email;
  }

  @Transient
  @NotNull
  public String getPassword() {
    final String email = getEmail();
    if (StringUtil.isEmptyOrSpaces(email)) return "";

    String password;
    try {
      password = PasswordSafe.getInstance().getPassword(null, StudyTaskManager.class, STEPIC_SETTINGS_PASSWORD_KEY + email);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get password for key [" + STEPIC_SETTINGS_PASSWORD_KEY + "]", e);
      password = "";
    }

    return StringUtil.notNullize(password);
  }

  @Transient
  public void setPassword(@NotNull final String password) {
    if (password.isEmpty()) return;
    try {
      PasswordSafe.getInstance().storePassword(null, StudyTaskManager.class, STEPIC_SETTINGS_PASSWORD_KEY + getEmail(), password);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't set password for key [" + STEPIC_SETTINGS_PASSWORD_KEY + getEmail() + "]", e);
    }
  }

  @NotNull
  public String getName() {
    return StringUtil.join(new String[]{firstName, lastName}, " ");
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public void setupTokenInfo(StepicWrappers.TokenInfo tokenInfo) {
    accessToken = tokenInfo.getAccessToken();
    refreshToken = tokenInfo.getRefreshToken();
  }

  public void update(StepicUser tmpUser) {
    id = tmpUser.getId();
    firstName = tmpUser.getFirstName();
    lastName = tmpUser.getLastName();
  }

  @Override
  public String toString() {
    return "StepicUser{" +
            "id=" + id +
            ", firstName='" + firstName + '\'' +
            ", email='" + email + '\'' +
            '}';
  }
}
