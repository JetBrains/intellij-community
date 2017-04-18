package com.jetbrains.edu.learning.stepic;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.StudyTaskManager;
import org.jetbrains.annotations.NotNull;

public class StepicUser {
  private static final String STEPIC_SETTINGS_PASSWORD_KEY = "STEPIC_SETTINGS_PASSWORD_KEY";
  private int id = -1;
  private String myFirstName;
  private String myLastName;
  private String myEmail;
  private String myAccessToken;
  private String myRefreshToken;
  private boolean isGuest;

  private StepicUser() {
    myFirstName = "";
    myLastName = "";
    myEmail = "";
    myAccessToken = "";
    myRefreshToken = "";
  }

  public static StepicUser createEmptyUser() {
    return new StepicUser();
  }
  
  public StepicUser(@NotNull final String email, @NotNull final String password, @NotNull StepicWrappers.TokenInfo tokenInfo) {
    this.myEmail = email;
    setPassword(password);
    setTokenInfo(tokenInfo);
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getFirstName() {
    return myFirstName;
  }

  public void setFirstName(final String firstName) {
    myFirstName = firstName;
  }

  public String getLastName() {
    return myLastName;
  }

  public void setLastName(final String lastName) {
    myLastName = lastName;
  }

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
    return StringUtil.notNullize(PasswordSafe.getInstance().getPassword(StudyTaskManager.class, STEPIC_SETTINGS_PASSWORD_KEY + login));
  }

  @Transient
  public void setPassword(@NotNull final String password) {
    if (password.isEmpty()) return;
    PasswordSafe.getInstance().setPassword(StudyTaskManager.class, STEPIC_SETTINGS_PASSWORD_KEY + getEmail(), password);
  }

  @NotNull
  public String getName() {
    return StringUtil.join(new String[]{myFirstName, myLastName}, " ");
  }

  @NotNull
  public String getAccessToken() {
    // for old project where authors were created with null tokens
    if (myAccessToken == null) {
      return "";
    }
    return myAccessToken;
  }

  public void setAccessToken(String accessToken) {
    this.myAccessToken = accessToken;
  }

  @NotNull
  public String getRefreshToken() {
    // for old project where authors were created with null tokens
    if (myRefreshToken == null) {
      return "";
    }
    return myRefreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.myRefreshToken = refreshToken;
  }

  public void setTokenInfo(@NotNull final StepicWrappers.TokenInfo tokenInfo) {
    myAccessToken = tokenInfo.getAccessToken();
    myRefreshToken = tokenInfo.getRefreshToken();
  }

  public boolean isGuest() {
    return isGuest;
  }

  public void setGuest(boolean guest) {
    isGuest = guest;
  }
}
