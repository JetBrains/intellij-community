
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.stepik;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class StepikConnectorInit {
  private static final Logger LOG = Logger.getInstance(StepikConnectorInit.class.getName());
  private static CloseableHttpClient ourClient;

  public static void initializeClient() {
    if (ourClient == null) {
        ourClient = getBuilder().build();
    }
  }

  public static void resetClient() {
    ourClient = null;
  }

  public static HttpClientBuilder getBuilder() {
    try {
    TrustManager[] trustAllCerts = getTrustAllCerts();
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAllCerts, new SecureRandom());
    return HttpClients
      .custom()
      .setMaxConnPerRoute(100000)
      .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
      .setSslcontext(sslContext);
    }
    catch (NoSuchAlgorithmException | KeyManagementException e) {
      LOG.error(e.getMessage());
    }
    return null;
  }

  @NotNull
  // now we support only one user
  public static CloseableHttpClient getHttpClient() {
    if (ourClient == null) {
      initializeClient();
    }
    return ourClient;
  }

  @Deprecated
  static void setHeaders(@NotNull final HttpRequestBase request, String contentType) {
    request.addHeader(new BasicHeader("content-type", contentType));
  }

  public static TrustManager[] getTrustAllCerts() {
    return new TrustManager[]{new X509TrustManager() {
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }

      public void checkClientTrusted(X509Certificate[] certs, String authType) {
      }

      public void checkServerTrusted(X509Certificate[] certs, String authType) {
      }
    }};
  }
}
