/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

public final class UrlImpl implements Url {
  private String externalForm;

  @Nullable
  private final String scheme;

  private final String authority;

  private final String path;
  private String decodedPath;

  private final String parameters;

  private String externalFormWithoutParameters;

  public UrlImpl(@Nullable String path) {
    this(null, null, path, null);
  }

  public UrlImpl(@NotNull String scheme, @Nullable String authority, @Nullable String path) {
    this(scheme, authority, path, null);
  }

  public UrlImpl(@Nullable String scheme, @Nullable String authority, @Nullable String path, @Nullable String parameters) {
    this.scheme = scheme;
    this.authority = StringUtil.nullize(authority);
    this.path = StringUtil.isEmpty(path) ? "/" : path;
    this.parameters = StringUtil.nullize(parameters);
  }

  @NotNull
  @Override
  public String getPath() {
    if (decodedPath == null) {
      decodedPath = URLUtil.unescapePercentSequences(path);
    }
    return decodedPath;
  }

  @Nullable
  @Override
  public String getScheme() {
    return scheme;
  }

  @Override
  @Nullable
  public String getAuthority() {
    return authority;
  }

  @Override
  public boolean isInLocalFileSystem() {
    return StandardFileSystems.FILE_PROTOCOL.equals(scheme);
  }

  @Nullable
  @Override
  public String getParametersPart() {
    return parameters;
  }

  @Override
  public String toDecodedForm(boolean skipQueryAndFragment) {
    StringBuilder builder = new StringBuilder().append(scheme).append("://");
    if (authority != null) {
      builder.append(authority);
    }
    builder.append(getPath());
    if (!skipQueryAndFragment && parameters != null) {
      builder.append(parameters);
    }
    return builder.toString();
  }

  @Override
  @NotNull
  public URI toJavaUriWithoutParameters() {
    try {
      String externalPath = path;
      boolean inLocalFileSystem = isInLocalFileSystem();
      if (inLocalFileSystem && SystemInfo.isWindows && externalPath.charAt(0) != '/') {
        externalPath = '/' + externalPath;
      }
      return new URI(scheme, inLocalFileSystem ? "" : authority, externalPath, null, null);
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @NotNull
  public String toExternalForm(boolean skipQueryAndFragment) {
    if (parameters == null || !skipQueryAndFragment) {
      if (externalForm != null) {
        return externalForm;
      }
    }
    else if (externalFormWithoutParameters != null) {
      return externalFormWithoutParameters;
    }

    String result = toJavaUriWithoutParameters().toASCIIString();
    if (skipQueryAndFragment) {
      externalFormWithoutParameters = result;
      if (parameters == null) {
        externalForm = externalFormWithoutParameters;
      }
    }
    else {
      if (parameters != null) {
        result += parameters;
      }
      externalForm = result;
    }
    return result;
  }

  @NotNull
  @Override
  public String toExternalForm() {
    return toExternalForm(false);
  }

  @Override
  public String toString() {
    return toExternalForm(false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UrlImpl)) {
      return false;
    }

    UrlImpl url = (UrlImpl)o;
    return equalsIgnoreParameters(url) && (parameters == null ? url.parameters == null : parameters.equals(url.parameters));
  }

  @Override
  public boolean equalsIgnoreParameters(@Nullable Url o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UrlImpl)) {
      if (o instanceof LocalFileUrl && isInLocalFileSystem()) {
        return o.getPath().equals(path);
      }
      return false;
    }

    UrlImpl url = (UrlImpl)o;
    if (scheme == null ? url.scheme == null : !scheme.equals(url.scheme)) {
      return false;
    }
    if (authority == null ? url.authority != null : !authority.equals(url.authority)) {
      return false;
    }
    String decodedPath = getPath();
    if (!decodedPath.equals(url.getPath())) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = scheme == null ? 0 : scheme.hashCode();
    result = 31 * result + (authority != null ? authority.hashCode() : 0);
    String decodedPath = getPath();
    result = 31 * result + decodedPath.hashCode();
    result = 31 * result + (parameters != null ? parameters.hashCode() : 0);
    return result;
  }
}