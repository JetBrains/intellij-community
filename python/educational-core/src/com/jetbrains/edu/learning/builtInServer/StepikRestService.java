/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.builtInServer;

import com.intellij.openapi.diagnostic.Logger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.RestService;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.edu.learning.builtInServer.Utils.*;

/**
 * @author meanmail
 */
public class StepikRestService extends RestService {
  private static final Logger LOG = Logger.getInstance(StepikRestService.class.getName());
  private static final String SERVICE_NAME = "edu/stepik";
  private static final Pattern OPEN_COURSE = Pattern.compile("/" + SERVICE_NAME + "/course/[^/]*-(\\d+)(?:$|\\?|/.*)");

  @NotNull
  @Override
  protected String getServiceName() {
    return SERVICE_NAME;
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.GET;
  }

  @Override
  protected boolean isPrefixlessAllowed() {
    return true;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context)
    throws IOException {
    LOG.info("Request: " + urlDecoder.path());

    String path = urlDecoder.path();
    Matcher matcher = OPEN_COURSE.matcher(path);
    if (matcher.matches()) {
      int courseId = Integer.parseInt(matcher.group(1));
      LOG.info("Open course: " + courseId);

      if (findOpenProjectAndFocus(courseId) || findRecentProjectAndOpen(courseId) || createProjectAndOpen(courseId)) {
        RestService.sendOk(request, context);
        LOG.info("Course opened: " + courseId);
        return null;
      }

      RestService.sendStatus(HttpResponseStatus.NOT_FOUND, false, context.channel());
      String message = "A project didn't found or created";
      LOG.info(message);
      return message;
    }

    RestService.sendStatus(HttpResponseStatus.BAD_REQUEST, false, context.channel());
    String message = "Unknown command: " + path;
    LOG.info(message);
    return message;
  }
}
