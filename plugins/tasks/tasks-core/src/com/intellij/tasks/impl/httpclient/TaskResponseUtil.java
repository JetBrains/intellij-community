// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl.httpclient;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.Producer;
import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * @author Mikhail Golubev
 */
public final class TaskResponseUtil {
  public static final Logger LOG = Logger.getInstance(TaskResponseUtil.class);

  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  /**
   * Utility class
   */
  private TaskResponseUtil() { }

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
      if (stream == null) return "";
      try (Reader reader = new InputStreamReader(stream, DEFAULT_CHARSET)) {
        return StreamUtil.readText(reader);
      }
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
    return charsetName != null ? new InputStreamReader(stream, charsetName) : new InputStreamReader(stream, DEFAULT_CHARSET);
  }

  public static @NotNull String messageForStatusCode(int statusCode) {
    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
      return TaskBundle.message("failure.login");
    }
    else if (statusCode == HttpStatus.SC_FORBIDDEN) {
      return TaskBundle.message("failure.permissions");
    }
    return TaskBundle.message("failure.http.error", statusCode, HttpStatus.getStatusText(statusCode));
  }

  @ApiStatus.Internal
  public static class JsonResponseHandlerBuilder {
    private final Gson myGson;
    private IntPredicate mySuccessChecker = (code) -> code / 100 == 2;
    private IntPredicate myIgnoreChecker = (code) -> false;
    private Function<HttpResponse, ? extends RequestFailedException> myErrorExtractor;

    private JsonResponseHandlerBuilder(@NotNull Gson gson) {
      myGson = gson;
    }

    public static @NotNull JsonResponseHandlerBuilder fromGson(@NotNull Gson gson) {
      return new JsonResponseHandlerBuilder(gson);
    }

    public @NotNull JsonResponseHandlerBuilder successCode(@NotNull IntPredicate predicate) {
      mySuccessChecker = predicate;
      return this;
    }

    public @NotNull JsonResponseHandlerBuilder ignoredCode(@NotNull IntPredicate predicate) {
      myIgnoreChecker = predicate;
      return this;
    }

    public @NotNull JsonResponseHandlerBuilder errorHandler(@NotNull Function<HttpResponse, ? extends RequestFailedException> handler) {
      myErrorExtractor = handler;
      return this;
    }

    public @NotNull <T> ResponseHandler<T> toSingleObject(@NotNull Class<T> cls) {
      return new GsonResponseHandler<>(this,
                                       s -> myGson.fromJson(s, cls),
                                       r -> myGson.fromJson(r, cls),
                                       () -> null);
    }

    public @NotNull <T> ResponseHandler<List<T>> toMultipleObjects(@NotNull TypeToken<List<T>> typeToken) {
      return new GsonResponseHandler<>(this,
                                       s -> myGson.fromJson(s, typeToken.getType()),
                                       r -> myGson.fromJson(r, typeToken.getType()),
                                       () -> Collections.emptyList());
    }

    public @NotNull ResponseHandler<Void> toNothing() {
      return new GsonResponseHandler<>(this,
                                       s -> null,
                                       r -> null,
                                       () -> null);
    }
  }

  public static class GsonResponseHandler<T> implements ResponseHandler<T> {
    private final JsonResponseHandlerBuilder myBuilder;
    private final @NotNull Function<? super String, ? extends T> myFromString;
    private final @NotNull Function<? super Reader, ? extends T> myFromReader;
    private final @NotNull Producer<? extends T> myFallbackValue;

    private GsonResponseHandler(@NotNull JsonResponseHandlerBuilder builder,
                                @NotNull Function<? super String, ? extends T> fromString,
                                @NotNull Function<? super Reader, ? extends T> fromReader,
                                @NotNull Producer<? extends T> fallbackValue) {
      myBuilder = builder;
      myFromString = fromString;
      myFromReader = fromReader;
      myFallbackValue = fallbackValue;
    }

    @Override
    public T handleResponse(HttpResponse response) throws IOException {
      int statusCode = response.getStatusLine().getStatusCode();
      if (!myBuilder.mySuccessChecker.test(statusCode)) {
        if (myBuilder.myIgnoreChecker.test(statusCode)) {
          return myFallbackValue.produce();
        }
        if (myBuilder.myErrorExtractor != null) {
          RequestFailedException exception = myBuilder.myErrorExtractor.apply(response);
          if (exception != null) {
            throw exception;
          }
        }
        throw RequestFailedException.forStatusCode(statusCode, messageForStatusCode(statusCode));
      }
      try {
        if (LOG.isDebugEnabled()) {
          String content = getResponseContentAsString(response);
          TaskUtil.prettyFormatJsonToLog(LOG, content);
          return myFromString.apply(content);
        }
        else {
          return myFromReader.apply(getResponseContentAsReader(response));
        }
      }
      catch (JsonSyntaxException e) {
        LOG.warn("Malformed server response", e);
        return myFallbackValue.produce();
      }
    }
  }

  public static final class GsonSingleObjectDeserializer<T> extends GsonResponseHandler<T> {
    public GsonSingleObjectDeserializer(@NotNull Gson gson, @NotNull Class<T> cls) {
      this(gson, cls, false);
    }

    public GsonSingleObjectDeserializer(@NotNull Gson gson, @NotNull Class<T> cls, boolean ignoreNotFound) {
      super(JsonResponseHandlerBuilder.fromGson(gson).ignoredCode(code -> ignoreNotFound && code == HttpStatus.SC_NOT_FOUND),
            s -> gson.fromJson(s, cls),
            r -> gson.fromJson(r, cls),
            () -> null);
    }
  }

  public static final class GsonMultipleObjectsDeserializer<T> extends GsonResponseHandler<List<T>> {
    public GsonMultipleObjectsDeserializer(Gson gson, TypeToken<List<T>> typeToken) {
      this(gson, typeToken, false);
    }

    public GsonMultipleObjectsDeserializer(@NotNull Gson gson, @NotNull TypeToken<List<T>> token, boolean ignoreNotFound) {
      super(JsonResponseHandlerBuilder.fromGson(gson).ignoredCode(code -> ignoreNotFound && code == HttpStatus.SC_NOT_FOUND),
            s -> gson.fromJson(s, token.getType()),
            r -> gson.fromJson(r, token.getType()),
            () -> Collections.emptyList());
    }
  }

  public static void prettyFormatResponseToLog(@NotNull Logger logger, @NotNull HttpMethod response) {
    if (logger.isDebugEnabled() && response.hasBeenUsed()) {
      try {
        String content = TaskResponseUtil.getResponseContentAsString(response);
        org.apache.commons.httpclient.Header header = response.getRequestHeader(HTTP.CONTENT_TYPE);
        String contentType = header == null ? "text/plain" : StringUtil.toLowerCase(header.getElements()[0].getName());
        if (contentType.contains("xml")) {
          TaskUtil.prettyFormatXmlToLog(logger, content);
        }
        else if (contentType.contains("json")) {
          TaskUtil.prettyFormatJsonToLog(logger, content);
        }
        else {
          logger.debug(content);
        }
      }
      catch (IOException e) {
        logger.error(e);
      }
    }
  }

  public static void prettyFormatResponseToLog(@NotNull Logger logger, @NotNull HttpResponse response) {
    if (logger.isDebugEnabled()) {
      try {
        String content = TaskResponseUtil.getResponseContentAsString(response);
        Header header = response.getEntity().getContentType();
        String contentType = header == null ? "text/plain" : StringUtil.toLowerCase(header.getElements()[0].getName());
        if (contentType.contains("xml")) {
          TaskUtil.prettyFormatXmlToLog(logger, content);
        }
        else if (contentType.contains("json")) {
          TaskUtil.prettyFormatJsonToLog(logger, content);
        }
        else {
          logger.debug(content);
        }
      }
      catch (IOException e) {
        logger.error(e);
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
