/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.builtInWebServer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.UriUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.NetUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.io.FileResponses;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.jetbrains.io.Responses.sendOptionsResponse;
import static org.jetbrains.io.Responses.sendStatus;

public final class BuiltInWebServer extends HttpRequestHandler {
  static final Logger LOG = Logger.getInstance(BuiltInWebServer.class);

  @Nullable
  public static VirtualFile findIndexFile(@NotNull VirtualFile basedir) {
    VirtualFile[] children = basedir.getChildren();
    if (children == null || children.length == 0) {
      return null;
    }

    for (String indexNamePrefix : new String[]{"index.", "default."}) {
      VirtualFile index = null;
      String preferredName = indexNamePrefix + "html";
      for (VirtualFile child : children) {
        if (!child.isDirectory()) {
          String name = child.getName();
          if (name.equals(preferredName)) {
            return child;
          }
          else if (index == null && name.startsWith(indexNamePrefix)) {
            index = child;
          }
        }
      }
      if (index != null) {
        return index;
      }
    }
    return null;
  }

  @Override
  public boolean isSupported(@NotNull FullHttpRequest request) {
    return super.isSupported(request) || request.method() == HttpMethod.POST || request.method() == HttpMethod.OPTIONS;
  }

  @Override
  public boolean process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) {
    if (request.method() == HttpMethod.OPTIONS) {
      sendOptionsResponse("GET, POST, HEAD, OPTIONS", request, context);
      return true;
    }

    String host = HttpHeaders.getHost(request);
    if (StringUtil.isEmpty(host)) {
      return false;
    }

    int portIndex = host.indexOf(':');
    if (portIndex > 0) {
      host = host.substring(0, portIndex);
    }

    String projectName;
    boolean isIpv6 = host.charAt(0) == '[' && host.length() > 2 && host.charAt(host.length() - 1) == ']';
    if (isIpv6) {
      host = host.substring(1, host.length() - 1);
    }

    if (isIpv6 || Character.digit(host.charAt(0), 10) != -1 || host.charAt(0) == ':' || isOwnHostName(host)) {
      if (urlDecoder.path().length() < 2) {
        return false;
      }
      projectName = null;
    }
    else {
      projectName = host;
    }
    return doProcess(request, context.channel(), projectName);
  }

  public static boolean isOwnHostName(@NotNull String host) {
    if (NetUtils.isLocalhost(host)) {
      return true;
    }

    try {
      InetAddress address = InetAddress.getByName(host);
      if (host.equals(address.getHostAddress()) || host.equalsIgnoreCase(address.getCanonicalHostName())) {
        return true;
      }

      String localHostName = InetAddress.getLocalHost().getHostName();
      // WEB-8889
      // develar.local is own host name: develar. equals to "develar.labs.intellij.net" (canonical host name)
      return localHostName.equalsIgnoreCase(host) ||
             (host.endsWith(".local") && localHostName.regionMatches(true, 0, host, 0, host.length() - ".local".length()));
    }
    catch (UnknownHostException ignored) {
      return false;
    }
  }

  private static boolean doProcess(@NotNull FullHttpRequest request, @NotNull Channel channel, @Nullable String projectName) {
    final String decodedPath = URLUtil.unescapePercentSequences(UriUtil.trimParameters(request.uri()));
    int offset;
    boolean emptyPath;
    boolean isCustomHost = projectName != null;
    if (isCustomHost) {
      // host mapped to us
      offset = 0;
      emptyPath = decodedPath.isEmpty();
    }
    else {
      offset = decodedPath.indexOf('/', 1);
      projectName = decodedPath.substring(1, offset == -1 ? decodedPath.length() : offset);
      emptyPath = offset == -1;
    }

    Project project = findProject(projectName, isCustomHost);
    if (project == null) {
      return false;
    }

    if (emptyPath) {
      if (!SystemInfoRt.isFileSystemCaseSensitive) {
        // may be passed path is not correct
        projectName = project.getName();
      }

      // we must redirect "jsdebug" to "jsdebug/" as nginx does, otherwise browser will treat it as file instead of directory, so, relative path will not work
      WebServerPathHandler.redirectToDirectory(request, channel, projectName);
      return true;
    }

    final String path = FileUtil.toCanonicalPath(decodedPath.substring(offset + 1), '/');
    LOG.assertTrue(path != null);

    for (WebServerPathHandler pathHandler : WebServerPathHandler.EP_NAME.getExtensions()) {
      try {
        if (pathHandler.process(path, project, request, channel, projectName, decodedPath, isCustomHost)) {
          return true;
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    return false;
  }

  static final class StaticFileHandler extends WebServerFileHandler {
    @Override
    public boolean process(@NotNull VirtualFile file,
                           @NotNull CharSequence canonicalRequestPath,
                           @NotNull Project project,
                           @NotNull FullHttpRequest request,
                           @NotNull Channel channel) throws IOException {
      File ioFile = VfsUtilCore.virtualToIoFile(file);
      if (hasAccess(ioFile)) {
        FileResponses.sendFile(request, channel, ioFile);
      }
      else {
        sendStatus(HttpResponseStatus.FORBIDDEN, channel, request);
      }
      return true;
    }

    private static boolean hasAccess(File result) {
      // deny access to .htaccess files
      return !result.isDirectory() && result.canRead() && !(result.isHidden() || result.getName().startsWith(".ht"));
    }
  }

  @Nullable
  private static Project findProject(String projectName, boolean isCustomHost) {
    // user can rename project directory, so, we should support this case - find project by base directory name
    Project candidateByDirectoryName = null;
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      String name = project.getName();
      // domain name is case-insensitive
      if (!project.isDisposed() && ((isCustomHost || !SystemInfoRt.isFileSystemCaseSensitive) ? projectName.equalsIgnoreCase(name) : projectName.equals(name))) {
        return project;
      }

      if (candidateByDirectoryName == null && compareNameAndProjectBasePath(projectName, project)) {
        candidateByDirectoryName = project;
      }
    }
    return candidateByDirectoryName;
  }

  public static boolean compareNameAndProjectBasePath(String projectName, Project project) {
    String basePath = project.getBasePath();
    return basePath != null && basePath.length() > projectName.length() && basePath.endsWith(projectName) && basePath.charAt(basePath.length() - projectName.length() - 1) == '/';
  }
}