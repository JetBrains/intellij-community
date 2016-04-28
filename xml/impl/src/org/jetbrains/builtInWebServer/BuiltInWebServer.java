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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.NetUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManagerImpl;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.io.FileResponses;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.io.Responses;
import org.jetbrains.notification.SingletonNotificationManager;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import static org.jetbrains.io.Responses.sendOptionsResponse;

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

  public boolean isAccessible(@NotNull HttpRequest request) { return NettyUtil.isLocalOrigin(request, false, true); }

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
    return doProcess(request, context, projectName);
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
    catch (IOException ignored) {
      return false;
    }
  }

  // private val notificationManager by lazy {
  // SingletonNotificationManager(BuiltInServerManagerImpl.NOTIFICATION_GROUP.getValue(), NotificationType.INFORMATION, null)
  // }
  private static final SingletonNotificationManager
    notificationManager = new SingletonNotificationManager(BuiltInServerManagerImpl.NOTIFICATION_GROUP.getValue(), NotificationType.INFORMATION, null);
  // internal fun isActivatable() = Registry.`is`("ide.built.in.web.server.activatable", false)
  static boolean isActivatable() { return Registry.is("ide.built.in.web.server.activatable", true); }

  private static boolean doProcess(@NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context, @Nullable String projectName) {
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

    if (isActivatable() && !PropertiesComponent.getInstance().getBoolean("ide.built.in.web.server.active", false)) {
      notificationManager.notify("Built-in web server is deactivated, to activate, please use Open in Browser", (Project)null);
      return false;
    }

    if (emptyPath) {
      if (!SystemInfoRt.isFileSystemCaseSensitive) {
        // may be passed path is not correct
        projectName = project.getName();
      }

      // we must redirect "jsdebug" to "jsdebug/" as nginx does, otherwise browser will treat it as file instead of directory, so, relative path will not work
      WebServerPathHandler.redirectToDirectory(request, context.channel(), projectName);
      return true;
    }

    final String path = toIdeaPath(decodedPath, offset);
    if (path == null) {
      LOG.warn(decodedPath + " is not valid");
      Responses.sendStatus(HttpResponseStatus.NOT_FOUND, context.channel(), request);
      return true;
    }

    for (WebServerPathHandler pathHandler : WebServerPathHandler.EP_NAME.getExtensions()) {
      try {
        if (pathHandler.process(path, project, request, context, projectName, decodedPath, isCustomHost)) {
          return true;
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    return false;
  }

  static boolean canBeAccessedDirectly(String path) {
    for (WebServerFileHandler fileHandler : WebServerFileHandler.EP_NAME.getExtensions()) {
      for (String ext: fileHandler.pageFileExtensions()) {
        if (FileUtilRt.extensionEquals(path, ext)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String toIdeaPath(String decodedPath, int offset) {
    // must be absolute path (relative to DOCUMENT_ROOT, i.e. scheme://authority/) to properly canonicalize
    String path = decodedPath.substring(offset);
    if (!path.startsWith("/")) {
      return null;
    }
    return FileUtil.toCanonicalPath(path, '/').substring(1);
  }


  static final class StaticFileHandler extends WebServerFileHandler {
    // override val pageFileExtensions = arrayOf("html", "htm", "shtml")
    private static List<String> ourPageFileExtensions = ContainerUtil.list("html", "htm", "shtml", "stm", "shtm");

    @Override
    protected List<String> pageFileExtensions() {
      return ourPageFileExtensions;
    }

    @Override
    public boolean process(@NotNull VirtualFile file,
                           @NotNull CharSequence canonicalRequestPath,
                           @NotNull Project project,
                           @NotNull FullHttpRequest request,
                           @NotNull Channel channel) throws IOException {
      FileResponses.sendFile(request, channel, VfsUtilCore.virtualToIoFile(file));
      return true;
    }

    static boolean checkAccess(Channel channel, File file, HttpRequest request, File root) {
      File parent = file;
      do {
        if (!hasAccess(parent)) {
          Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request);
          return false;
        }
        parent = parent.getParentFile();
        if (parent == null) break;
      }
      while (!FileUtil.filesEqual(parent, root));
      return true;
    }

    private static boolean hasAccess(File result) {
      // deny access to any dot prefixed file
      return result.canRead() && !(result.isHidden() || result.getName().startsWith("."));
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