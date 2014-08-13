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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.Responses;

import java.net.URI;

/**
 * By default {@link WebServerPathToFileManager} will be used to map request to file.
 * If file physically exists in the file system, you must use {@link WebServerRootsProvider}.
 *
 * Consider to extend {@link WebServerPathHandlerAdapter} instead of implement low-level {@link #process(String, com.intellij.openapi.project.Project, io.netty.handler.codec.http.FullHttpRequest, io.netty.channel.Channel, String, String, boolean)}
 */
public abstract class WebServerPathHandler {
  static final ExtensionPointName<WebServerPathHandler> EP_NAME = ExtensionPointName.create("org.jetbrains.webServerPathHandler");

  public abstract boolean process(@NotNull String path,
                                  @NotNull Project project,
                                  @NotNull FullHttpRequest request,
                                  @NotNull Channel channel,
                                  @Nullable String projectName,
                                  @NotNull String decodedRawPath,
                                  boolean isCustomHost);

  protected static void redirectToDirectory(@NotNull HttpRequest request, @NotNull Channel channel, @NotNull String path) {
    FullHttpResponse response = Responses.response(HttpResponseStatus.MOVED_PERMANENTLY);
    URI url = VfsUtil.toUri("http://" + HttpHeaders.getHost(request) + '/' + path + '/');
    BuiltInWebServer.LOG.assertTrue(url != null);
    response.headers().add(HttpHeaders.Names.LOCATION, url.toASCIIString());
    Responses.send(response, channel, request);
  }

  protected static boolean endsWithSlash(@NotNull String path) {
    return path.charAt(path.length() - 1) == '/';
  }
}