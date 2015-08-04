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
package com.jetbrains.edu.learning.stepic;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("MethodMayBeStatic")
@State(
  name = "GithubSettings",
  storages = {@Storage(
    file = StoragePathMacros.APP_CONFIG + "/stepic_settings.xml")})
public class EduSettings implements PersistentStateComponent<EduSettings> {

  @Nullable public String LOGIN = null;

  private static final String STEPIC_SETTINGS_PASSWORD_KEY = "STEPIC_SETTINGS_PASSWORD_KEY";
  private static final Logger LOG = Logger.getInstance(EduSettings.class.getName());

  public static EduSettings getInstance() {
    return ServiceManager.getService(EduSettings.class);
  }

  @NotNull
  public String getPassword() {
    String password;
    try {
      password = PasswordSafe.getInstance().getPassword(null, EduSettings.class, STEPIC_SETTINGS_PASSWORD_KEY);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get password for key [" + STEPIC_SETTINGS_PASSWORD_KEY + "]", e);
      password = "";
    }

    return StringUtil.notNullize(password);
  }

  public void setPassword(@NotNull String password) {
    try {
      PasswordSafe.getInstance().storePassword(null, EduSettings.class, STEPIC_SETTINGS_PASSWORD_KEY, password);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't set password for key [" + STEPIC_SETTINGS_PASSWORD_KEY + "]", e);
    }
  }
  @Nullable
  public String getLogin() {
    return LOGIN;
  }

  public void setLogin(@Nullable String login) {
    LOGIN = login;
  }
  @Nullable
  @Override
  public EduSettings getState() {
    return this;
  }

  @Override
  public void loadState(EduSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}