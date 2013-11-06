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

import com.google.common.base.CharMatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// We don't use Java URI due to problem — http://cns-etuat-2.localnet.englishtown.com/school/e12/#school/45383/201/221/382?c=countrycode=cc|culturecode=en-us|partnercode=mkge
// it is illegal URI (fragment before query), but we must support such URI
public final class Urls {
  private static final Logger LOG = Logger.getInstance(Urls.class);

  public static final CharMatcher SLASH_MATCHER = CharMatcher.is('/');

  // about ";" see WEB-100359
  private static final Pattern URI_PATTERN = Pattern.compile("^([^:/?#]+)://([^/?#]*)([^?#;]*)(.*)");

  @NotNull
  public static Url newFromEncoded(@NotNull String url) {
    Url result = parse(url, true);
    LOG.assertTrue(result != null);
    return result;
  }

  @NotNull
  public static Url newHttpUrl(@Nullable String authority, @Nullable String path) {
    return new UrlImpl("http", authority, path);
  }

  @Nullable
  public static Url parse(@NotNull String url, boolean asLocalIfNoScheme) {
    if (asLocalIfNoScheme && !URLUtil.containsScheme(url)) {
      // nodejs debug — files only in local filesystem
      return new LocalFileUrl(url);
    }
    return parseUrl(VfsUtil.toIdeaUrl(url), true);
  }

  @Nullable
  public static URI parseAsJavaUriWithoutParameters(@NotNull String url) {
    Url asUrl = parseUrl(url, false);
    if (asUrl == null) {
      return null;
    }

    try {
      return asUrl.toJavaUriWithoutParameters();
    }
    catch (Exception e) {
      LOG.info("Can't parse " + url, e);
      return null;
    }
  }

  @Nullable
  private static Url parseUrl(@NotNull String url, boolean urlAsRaw) {
    String urlToParse;
    if (url.startsWith("jar:file://")) {
      urlToParse = url.substring("jar:".length());
    }
    else {
      urlToParse = url;
    }

    Matcher matcher = URI_PATTERN.matcher(urlToParse);
    if (!matcher.matches()) {
      LOG.warn("Cannot parse url " + url);
      return null;
    }
    String scheme = matcher.group(1);
    if (urlToParse != url) {
      scheme = "jar:" + scheme;
    }

    String authority = StringUtil.nullize(matcher.group(2));

    String path = StringUtil.nullize(matcher.group(3));
    if (path != null) {
      path = FileUtil.toCanonicalUriPath(path);
    }

    String parameters = matcher.group(4);
    if (authority != null && StandardFileSystems.FILE_PROTOCOL.equals(scheme)) {
      path = path == null ? authority : (authority + path);
      authority = null;
    }
    return new UrlImpl(urlAsRaw ? url : null, scheme, authority, path, parameters);
  }

  // java.net.URI.create cannot parse "file:///Test Stuff" - but you don't need to worry about it - this method is aware
  @Nullable
  public static Url newFromIdea(@NotNull String url) {
    return URLUtil.containsScheme(url) ? parseUrl(VfsUtil.toIdeaUrl(url), false) : new LocalFileUrl(url);
  }

  // must not be used in NodeJS
  public static Url newFromVirtualFile(@NotNull VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      return new UrlImpl(file.getFileSystem().getProtocol(), null, file.getPath());
    }
    else {
      return parseUrl(file.getUrl(), false);
    }
  }

  public static boolean equalsIgnoreParameters(@NotNull Url url, @NotNull VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      return url.isInLocalFileSystem() && url.getPath().equals(file.getPath());
    }
    else if (url.isInLocalFileSystem()) {
      return false;
    }

    Url fileUrl = parseUrl(file.getUrl(), false);
    return fileUrl != null && fileUrl.equalsIgnoreParameters(url);
  }
}