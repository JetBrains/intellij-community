package com.intellij.tasks.impl.httpclient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.impl.TaskUtil;
import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.HttpMethod;
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
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class ResponseUtil {
  public static final Logger LOG = Logger.getInstance(ResponseUtil.class);

  public static final String DEFAULT_CHARSET_NAME = CharsetToolkit.UTF8;
  public final static Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_CHARSET_NAME);

  /**
   * Utility class
   */
  private ResponseUtil() {
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
      return stream == null ? "" : StreamUtil.readText(stream, DEFAULT_CHARSET_NAME);
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
    public GsonSingleObjectDeserializer(Gson gson, Class<T> cls) {
      myGson = gson;
      myClass = cls;
    }

    @Override
    public T handleResponse(HttpResponse response) throws IOException {
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 400 && statusCode < 500) {
        return null;
      }
      if (LOG.isDebugEnabled()) {
        String content = getResponseContentAsString(response);
        TaskUtil.prettyFormatJsonToLog(LOG, content);
        return myGson.fromJson(content, myClass);
      }
      return myGson.fromJson(getResponseContentAsReader(response), myClass);
    }
  }

  public static final class GsonMultipleObjectsDeserializer<T> implements ResponseHandler<List<T>> {
    private final Gson myGson;
    private final TypeToken<List<T>> myTypeToken;
    public GsonMultipleObjectsDeserializer(Gson gson, TypeToken<List<T>> token) {
      myGson = gson;
      myTypeToken = token;
    }

    @Override
    public List<T> handleResponse(HttpResponse response) throws IOException {
      if (LOG.isDebugEnabled()) {
        String content = getResponseContentAsString(response);
        TaskUtil.prettyFormatJsonToLog(LOG, content);
        return myGson.fromJson(content, myTypeToken.getType());
      }
      return myGson.fromJson(getResponseContentAsReader(response), myTypeToken.getType());
    }
  }
}
