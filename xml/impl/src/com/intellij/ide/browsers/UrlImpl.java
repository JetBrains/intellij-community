package com.intellij.ide.browsers;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

public final class UrlImpl implements Url {
  private String raw;
  private final String scheme;
  private final String authority;

  private final String path;
  private String decodedPath;

  private final String parameters;

  public UrlImpl(@Nullable String raw, @NotNull String scheme, @Nullable String authority, @Nullable String path, @Nullable String parameters) {
    this.raw = raw;
    this.scheme = scheme;
    this.authority = StringUtil.nullize(authority);
    this.path = StringUtil.nullize(path);
    this.parameters = StringUtil.nullize(parameters);
  }

  @Nullable
  @Override
  public String getPath() {
    if (decodedPath == null && path != null) {
      decodedPath = URLUtil.unescapePercentSequences(path);
    }
    return decodedPath;
  }

  @Nullable
  @Override
  public String getScheme() {
    return scheme;
  }

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
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(scheme).append("://");
      if (authority != null) {
        builder.append(authority);
      }
      if (path != null) {
        builder.append(getPath());
      }
      if (!skipQueryAndFragment && parameters != null) {
        builder.append(parameters);
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Override
  @NotNull
  public String toExternalForm() {
    if (raw == null) {
      try {
        String externalPath = path;
        boolean inLocalFileSystem = isInLocalFileSystem();
        if (inLocalFileSystem && SystemInfo.isWindows && externalPath.charAt(0) != '/') {
          externalPath = '/' + externalPath;
        }
        raw = new URI(scheme, inLocalFileSystem ? "" : authority, externalPath, null, null).toASCIIString();
        if (parameters != null) {
          raw += parameters;
        }
      }
      catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
    return raw;
  }

  @Override
  public String toString() {
    return raw;
  }
}
