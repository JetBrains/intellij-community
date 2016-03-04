/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.edu.stepic;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("MethodMayBeStatic")
@State(name = "StudySettings", storages = @Storage("stepic_settings.xml"))
public class StudySettings implements PersistentStateComponent<StudySettings.State> {

  private State myState = new State();

  public static class State {
    @Nullable public String LOGIN = null;
  }

  private static final String STEPIC_SETTINGS_PASSWORD_KEY = "STEPIC_SETTINGS_PASSWORD_KEY";
  private static final Logger LOG = Logger.getInstance(StudySettings.class.getName());

  public static StudySettings getInstance() {
    return ServiceManager.getService(StudySettings.class);
  }

  @NotNull
  public String getPassword() {
    final String login = getLogin();
    if (StringUtil.isEmptyOrSpaces(login)) return "";

    String password;
    try {
      password = PasswordSafe.getInstance().getPassword(null, StudySettings.class, STEPIC_SETTINGS_PASSWORD_KEY);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get password for key [" + STEPIC_SETTINGS_PASSWORD_KEY + "]", e);
      password = "";
    }

    return StringUtil.notNullize(password);
  }

  public void setPassword(@NotNull String password) {
    try {
      PasswordSafe.getInstance().storePassword(null, StudySettings.class, STEPIC_SETTINGS_PASSWORD_KEY, password);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't set password for key [" + STEPIC_SETTINGS_PASSWORD_KEY + "]", e);
    }
  }

  @Nullable
  public String getLogin() {
    return myState.LOGIN;
  }

  public void setLogin(@Nullable String login) {
    myState.LOGIN = login;
  }
  @Nullable
  @Override
  public StudySettings.State getState() {
    return myState;
  }

  @Override
  public void loadState(StudySettings.State state) {
    myState = state;
  }
}