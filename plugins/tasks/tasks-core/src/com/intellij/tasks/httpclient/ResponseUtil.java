package com.intellij.tasks.httpclient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.impl.TaskUtil;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class ResponseUtil {
  public static final Logger LOG = Logger.getInstance(ResponseUtil.class);
  // TODO: in JDK7 see StandardCharsets
  public final static Charset DEFAULT_CHARSET = Charset.forName(CharsetToolkit.UTF8);

  /**
   * Utility class
   */
  private ResponseUtil() {
  }

  public static Reader getResponseContentAsReader(HttpResponse response) throws IOException {
    Header header = response.getEntity().getContentEncoding();
    Charset charset = header == null ? DEFAULT_CHARSET : Charset.forName(header.getValue());
    return new InputStreamReader(response.getEntity().getContent(), charset);
  }

  public static String getResponseContentAsString(HttpResponse response) throws IOException {
    return EntityUtils.toString(response.getEntity(), DEFAULT_CHARSET);
  }


  public static final class GsonSingleObjectDeserializer<T> implements ResponseHandler<T> {
    private final Gson myGson;
    private final Class<T> myClass;
    public GsonSingleObjectDeserializer(Gson gson, Class<T> cls) {
      myGson = gson;
      myClass = cls;
    }

    @Override
    public T handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
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
    public List<T> handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
      if (LOG.isDebugEnabled()) {
        String content = getResponseContentAsString(response);
        TaskUtil.prettyFormatJsonToLog(LOG, content);
        return myGson.fromJson(content, myTypeToken.getType());
      }
      return myGson.fromJson(getResponseContentAsReader(response), myTypeToken.getType());
    }
  }
}
