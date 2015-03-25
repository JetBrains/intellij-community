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
package com.intellij.tasks.impl.httpclient;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.TaskUtil;
import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class TaskResponseUtil {
  public static final Logger LOG = Logger.getInstance(TaskResponseUtil.class);

  public static final String DEFAULT_CHARSET_NAME = CharsetToolkit.UTF8;
  public final static Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_CHARSET_NAME);

  /**
   * Utility class
   */
  private TaskResponseUtil() {
  }

  public static Reader getResponseContentAsReader(@NotNull HttpResponse response) throws IOException {
    Header header = response.getEntity().getContentEncoding();
    Charset charset = header == null ? DEFAULT_CHARSET : Charset.forName(header.getValue());
    return new InputStreamReader(response.getEntity().getContent(), charset);
  }

  public static String getResponseContentAsString(@NotNull HttpResponse response) throws IOException {
    return EntityUtils.toString(response.getEntity(), DEFAULT_CHARSET);
  }

  public static String getResponseContentAsString(@NotNull HttpMethod response) throws IOException {
    // Sometimes servers don't specify encoding and HttpMethod#getResponseBodyAsString
    // by default decodes from Latin-1, so we got to read byte stream and decode it from UTF-8
    // manually
    //if (!response.hasBeenUsed()) {
    //  return "";
    //}
    org.apache.commons.httpclient.Header header = response.getResponseHeader(HTTP.CONTENT_TYPE);
    if (header != null && header.getValue().contains("charset")) {
      // ISO-8859-1 if charset wasn't specified in response
      return StringUtil.notNullize(response.getResponseBodyAsString());
    }
    else {
      InputStream stream = response.getResponseBodyAsStream();
      return stream == null ? "" : StreamUtil.readText(stream, DEFAULT_CHARSET);
    }
  }

  public static Reader getResponseContentAsReader(@NotNull HttpMethod response) throws IOException {
    //if (!response.hasBeenUsed()) {
    //  return new StringReader("");
    //}
    InputStream stream = response.getResponseBodyAsStream();
    String charsetName = null;
    org.apache.commons.httpclient.Header header = response.getResponseHeader(HTTP.CONTENT_TYPE);
    if (header != null) {
      // find out encoding
      for (HeaderElement part : header.getElements()) {
        NameValuePair pair = part.getParameterByName("charset");
        if (pair != null) {
          charsetName = pair.getValue();
        }
      }
    }
    return new InputStreamReader(stream, charsetName == null ? DEFAULT_CHARSET_NAME : charsetName);
  }


  public static final class GsonSingleObjectDeserializer<T> implements ResponseHandler<T> {
    private final Gson myGson;
    private final Class<T> myClass;
    private final boolean myIgnoreNotFound;

    public GsonSingleObjectDeserializer(@NotNull Gson gson, @NotNull Class<T> cls) {
      this(gson, cls, false);
    }

    public GsonSingleObjectDeserializer(@NotNull Gson gson, @NotNull Class<T> cls, boolean ignoreNotFound) {
      myGson = gson;
      myClass = cls;
      myIgnoreNotFound = ignoreNotFound;
    }

    @Override
    public T handleResponse(HttpResponse response) throws IOException {
      int statusCode = response.getStatusLine().getStatusCode();
      if (!isSuccessful(statusCode)) {
        if (statusCode == HttpStatus.SC_NOT_FOUND && myIgnoreNotFound) {
          return null;
        }
        throw RequestFailedException.forStatusCode(statusCode);
      }
      try {
        if (LOG.isDebugEnabled()) {
          String content = getResponseContentAsString(response);
          TaskUtil.prettyFormatJsonToLog(LOG, content);
          return myGson.fromJson(content, myClass);
        }
        else {
          return myGson.fromJson(getResponseContentAsReader(response), myClass);
        }
      }
      catch (JsonSyntaxException e) {
        LOG.warn("Malformed server response", e);
        return null;
      }
    }
  }

  public static final class GsonMultipleObjectsDeserializer<T> implements ResponseHandler<List<T>> {
    private final Gson myGson;
    private final TypeToken<List<T>> myTypeToken;
    private final boolean myIgnoreNotFound;

    public GsonMultipleObjectsDeserializer(Gson gson, TypeToken<List<T>> typeToken) {
      this(gson, typeToken, false);
    }

    public GsonMultipleObjectsDeserializer(@NotNull Gson gson, @NotNull TypeToken<List<T>> token, boolean ignoreNotFound) {
      myGson = gson;
      myTypeToken = token;
      myIgnoreNotFound = ignoreNotFound;
    }

    @Override
    public List<T> handleResponse(HttpResponse response) throws IOException {
      int statusCode = response.getStatusLine().getStatusCode();
      if (!isSuccessful(statusCode)) {
        if (statusCode == HttpStatus.SC_NOT_FOUND && myIgnoreNotFound) {
          return Collections.emptyList();
        }
        throw RequestFailedException.forStatusCode(statusCode);
      }
      try {
        if (LOG.isDebugEnabled()) {
          String content = getResponseContentAsString(response);
          TaskUtil.prettyFormatJsonToLog(LOG, content);
          return myGson.fromJson(content, myTypeToken.getType());
        }
        else {
          return myGson.fromJson(getResponseContentAsReader(response), myTypeToken.getType());
        }
      }
      catch (JsonSyntaxException e) {
        LOG.warn("Malformed server response", e);
        return Collections.emptyList();
      }
      catch (NumberFormatException e) {
        LOG.error("NFE in response: " + getResponseContentAsString(response), e);
        throw new RequestFailedException("Malformed response");
      }
    }
  }

  public static boolean isSuccessful(int statusCode) {
    return statusCode / 100 == 2;
  }

  public static boolean isClientError(int statusCode) {
    return statusCode / 100 == 4;
  }

  public static boolean isServerError(int statusCode) {
    return statusCode / 100 == 5;
  }
}
