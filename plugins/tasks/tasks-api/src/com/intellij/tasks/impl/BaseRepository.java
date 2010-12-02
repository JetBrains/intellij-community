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

import com.intellij.openapi.util.PasswordUtil;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;

/**
 * @author Dmitry Avdeev
 */
public abstract class BaseRepository extends TaskRepository {
  protected String myUsername = "";
  protected String myPassword = "";
  protected boolean myUseProxy;
  protected boolean myUseHttpAuthentication;

  public BaseRepository(TaskRepositoryType type) {
    super(type);
  }

  public BaseRepository(BaseRepository other) {
    super(other);
    myPassword = other.getPassword();
    myUsername = other.getUsername();
    myUseProxy = other.myUseProxy;
    myUseHttpAuthentication = other.myUseHttpAuthentication;
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

  @Override
  public abstract BaseRepository clone();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BaseRepository)) return false;
    if (!super.equals(o)) return false;

    BaseRepository that = (BaseRepository)o;

    if (getUrl() != null ? !getUrl().equals(that.getUrl()) : that.getUrl() != null) return false;
    if (getPassword() != null ? !getPassword().equals(that.getPassword()) : that.getPassword() != null) return false;
    if (getUsername() != null ? !getUsername().equals(that.getUsername()) : that.getUsername() != null) return false;

    return myUseProxy == that.myUseProxy && myUseHttpAuthentication == that.myUseHttpAuthentication;
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
}
