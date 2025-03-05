// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.util.Comparing;
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
import java.util.Objects;
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
  protected boolean myPasswordLoaded;

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
    if (!myPasswordLoaded) {
      myPasswordLoaded = true;
      loadPassword();
    }
    return myPassword;
  }

  private void loadPassword() {
    if (StringUtil.isEmpty(getPassword())) {
      CredentialAttributes attributes = getAttributes();
      Credentials credentials = PasswordSafe.getInstance().get(attributes);
      if (credentials != null) {
        setPassword(credentials.getPasswordAsString());
      }
    }
    else {
      storeCredentials();
    }
  }

  public void storeCredentials() {
    CredentialAttributes attributes = getAttributes();
    PasswordSafe.getInstance().set(attributes, new Credentials(getUsername(), getPassword()));
  }

  protected @NotNull CredentialAttributes getAttributes() {
    String serviceName = CredentialAttributesKt.generateServiceName("Tasks", getRepositoryType().getName() + " " + getUrl());
    return new CredentialAttributes(serviceName, getUsername());
  }

  @Override
  public abstract @NotNull BaseRepository clone();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BaseRepository that)) return false;
    if (!super.equals(o)) return false;

    if (!Objects.equals(getUrl(), that.getUrl())) return false;
    if (!Objects.equals(getPassword(), that.getPassword())) return false;
    if (!Objects.equals(getUsername(), that.getUsername())) return false;
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

  @Override
  public @Nullable CustomTaskState getPreferredOpenTaskState() {
    return myPreferredOpenTaskState;
  }

  @Override
  public void setPreferredCloseTaskState(@Nullable CustomTaskState state) {
    myPreferredCloseTaskState = state;
  }

  @Override
  public @Nullable CustomTaskState getPreferredCloseTaskState() {
    return myPreferredCloseTaskState;
  }

  @Override
  public @Nullable String extractId(@NotNull String taskName) {
    Matcher matcher = PATTERN.matcher(taskName);
    return matcher.find() ? matcher.group() : null;
  }

  @Override
  public void setUrl(String url) {
    super.setUrl(addSchemeIfNoneSpecified(url));
  }

  protected @NotNull String getDefaultScheme() {
    return "http";
  }

  private @Nullable String addSchemeIfNoneSpecified(@Nullable String url) {
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
