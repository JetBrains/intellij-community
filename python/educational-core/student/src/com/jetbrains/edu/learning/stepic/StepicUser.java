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
  private String myFirstName = "";
  private String myLastName = "";
  private String myEmail = "";

  public StepicUser() {
  }
  
  public StepicUser(@NotNull final String email, @NotNull final String password) {
    this.myEmail = email;
    setPassword(password);
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  @NotNull
  public String getFirstName() {
    return myFirstName;
  }

  public void setFirstName(@NotNull final String firstName) {
    this.myFirstName = firstName;
  }

  @NotNull
  public String getLastName() {
    return myLastName;
  }

  public void setLastName(@NotNull final String last_name) {
    this.myLastName = last_name;
  }

  @NotNull
  public String getEmail() {
    return myEmail;
  }

  public void setEmail(@NotNull final String email) {
    this.myEmail = email;
  }

  @Transient
  @NotNull
  public String getPassword() {
    final String login = getEmail();
    if (StringUtil.isEmptyOrSpaces(login)) return "";

    String password;
    try {
      password = PasswordSafe.getInstance().getPassword(null, StudyTaskManager.class, STEPIC_SETTINGS_PASSWORD_KEY + login);
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
    return StringUtil.join(new String[]{myFirstName, myLastName}, " ");
  }
}
