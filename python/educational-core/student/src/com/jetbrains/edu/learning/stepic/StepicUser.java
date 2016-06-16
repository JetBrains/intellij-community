package com.jetbrains.edu.learning.stepic;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.StudyTaskManager;

public class StepicUser {
  private static final String STEPIC_SETTINGS_PASSWORD_KEY = "STEPIC_SETTINGS_PASSWORD_KEY";
  private static final Logger LOG = Logger.getInstance(StepicUser.class);
  int id;
  String firstName;
  String lastName;
  String email;

  public StepicUser() {
  }
  
  public StepicUser(String email, String password) {
    this.email = email;
    setPassword(password);
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String last_name) {
    this.lastName = last_name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

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

  public void setPassword(String password) {
    try {
      PasswordSafe.getInstance().storePassword(null, StudyTaskManager.class, STEPIC_SETTINGS_PASSWORD_KEY + getEmail(), password);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't set password for key [" + STEPIC_SETTINGS_PASSWORD_KEY + getEmail() + "]", e);
    }
  }

  public String getName() {
    return StringUtil.join(new String[]{firstName, lastName}, " ");
  }
}
