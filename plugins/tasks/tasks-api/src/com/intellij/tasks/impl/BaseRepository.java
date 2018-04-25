/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.tasks.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
public abstract class BaseRepository extends TaskRepository {
  private static final Pattern PATTERN = Pattern.compile("[A-Z]+\\-\\d+");
  protected String myUsername = "";
  protected String myPassword = "";
  protected boolean myUseProxy;
  protected boolean myUseHttpAuthentication;
  protected boolean myLoginAnonymously;
  protected CustomTaskState myPreferredOpenTaskState;
  protected CustomTaskState myPreferredCloseTaskState;

  public BaseRepository(TaskRepositoryType type) {
    super(type);
  }

  public BaseRepository(BaseRepository other) {
    super(other);
    myPassword = other.getPassword();
    myUsername = other.getUsername();
    myUseProxy = other.myUseProxy;
    myUseHttpAuthentication = other.myUseHttpAuthentication;
    myLoginAnonymously = other.myLoginAnonymously;
  }

  public BaseRepository() {
  }

  public void setUsername(String username) {
    myUsername = username;
  }

  public void setPassword(String password) {
    myPassword = password;
  }

  @Tag("username")
  public String getUsername() {
    return myUsername;
  }

  @Transient
  public String getPassword() {
    return myPassword;
  }

  @Tag("password")
  public String getEncodedPassword() {
    return PasswordUtil.encodePassword(getPassword());
  }

  public void setEncodedPassword(String password) {
    try {
      setPassword(PasswordUtil.decodePassword(password));
    }
    catch (NumberFormatException e) {
      // do nothing
    }
  }

  @NotNull
  @Override
  public abstract BaseRepository clone();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BaseRepository)) return false;
    if (!super.equals(o)) return false;

    BaseRepository that = (BaseRepository)o;

    if (!Comparing.equal(getUrl(), that.getUrl())) return false;
    if (!Comparing.equal(getPassword(), that.getPassword())) return false;
    if (!Comparing.equal(getUsername(), that.getUsername())) return false;
    if (!Comparing.equal(isLoginAnonymously(), that.isLoginAnonymously())) return false;
    if (!Comparing.equal(isUseProxy(), that.isUseProxy())) return false;
    if (!Comparing.equal(isUseHttpAuthentication(), that.isUseHttpAuthentication())) return false;

    return true;
  }

  public boolean isUseProxy() {
    return myUseProxy;
  }

  public void setUseProxy(boolean useProxy) {
    myUseProxy = useProxy;
  }

  public boolean isUseHttpAuthentication() {
    return myUseHttpAuthentication;
  }

  public void setUseHttpAuthentication(boolean useHttpAuthentication) {
    myUseHttpAuthentication = useHttpAuthentication;
  }

  public boolean isLoginAnonymously() {
    return myLoginAnonymously;
  }

  public void setLoginAnonymously(final boolean loginAnonymously) {
    myLoginAnonymously = loginAnonymously;
  }

  @Override
  public void setPreferredOpenTaskState(@Nullable CustomTaskState state) {
    myPreferredOpenTaskState = state;
  }

  @Nullable
  @Override
  public CustomTaskState getPreferredOpenTaskState() {
    return myPreferredOpenTaskState;
  }

  @Override
  public void setPreferredCloseTaskState(@Nullable CustomTaskState state) {
    myPreferredCloseTaskState = state;
  }

  @Nullable
  @Override
  public CustomTaskState getPreferredCloseTaskState() {
    return myPreferredCloseTaskState;
  }

  @Nullable
  public String extractId(@NotNull String taskName) {
    Matcher matcher = PATTERN.matcher(taskName);
    return matcher.find() ? matcher.group() : null;
  }

  @Override
  public void setUrl(String url) {
    super.setUrl(addSchemeIfNoneSpecified(url));
  }

  @NotNull
  protected String getDefaultScheme() {
    return "http";
  }

  @Nullable
  private String addSchemeIfNoneSpecified(@Nullable String url) {
    if (StringUtil.isNotEmpty(url)) {
      try {
        final String scheme = new URI(url).getScheme();
        // For URL like "foo.bar:8080" host name will be parsed as scheme
        if (scheme == null) {
          url = getDefaultScheme() + "://" + url;
        }
      }
      catch (URISyntaxException ignored) {
      }
    }
    return url;
  }
}
