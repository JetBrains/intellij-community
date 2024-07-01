// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.security.ssl;

import sun.security.provider.X509Factory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class SslIDEConfirmingTrustStore {

  public static final String CHECK_CLIENT_TRUSTED = "----------checkClientTrusted----------";
  public static final String CHECK_SERVER_TRUSTED = "----------checkServerTrusted----------";
  public static final String IDE_DELEGATE_TRUST_MANAGER = "----------IdeDelegateTrustManager----------";
  public static final String DELEGATE_RESPONSE = "----------RESPONSE----------";
  public static final String DELEGATE_RESPONSE_OK = "----------OK----------";
  public static final String DELEGATE_RESPONSE_ERROR = "----------ERROR----------";


  private static final Multiplexor ourMultiplexor = new Multiplexor();

  public static void setup() {
    try {
      startMultiplexorThread();
      TrustManager delegateTM = new IdeDelegateTrustManager();
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[]{delegateTM}, null);

      // You don't have to set this as the default context,
      // it depends on the library you're using.
      SSLContext.setDefault(sslContext);
    }
    catch (NoSuchAlgorithmException | KeyManagementException ignore) {
    }
  }

  private static void startMultiplexorThread() {
    ourMultiplexor.start();
  }

  private static class IdeDelegateTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      checkTrusted(chain, authType, CHECK_CLIENT_TRUSTED);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private void sendAndWaitForResponse(PrintStream out, Integer key) throws CertificateException {
      synchronized (this) {
        System.out.println(out);
      }
      ourMultiplexor.waitForResponse(key);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      checkTrusted(chain, authType, CHECK_SERVER_TRUSTED);
    }

    private void checkTrusted(X509Certificate[] chain, String authType, String methodSignature) throws CertificateException {
      OutputStream os = new ByteArrayOutputStream();
      Integer key = ourMultiplexor.getKey();
      try (PrintStream out = new PrintStream(os, false, "ISO-8859-1")) {
        out.println(IDE_DELEGATE_TRUST_MANAGER);
        out.println(methodSignature);
        out.println(key);
        out.println(chain.length);
        out.println(authType);

        for (X509Certificate certificate : chain) {
          out.println(X509Factory.BEGIN_CERT);

          try {
            out.println(Base64.getMimeEncoder(80, "\n".getBytes(StandardCharsets.US_ASCII))
                          .encodeToString(certificate.getEncoded()));


          }
          catch (CertificateEncodingException e) {
            out.println("#ERROR: " + e.getMessage());
          }
          out.println(X509Factory.END_CERT);
        }
        out.println(methodSignature);
        sendAndWaitForResponse(out, key);
      }
      catch (UnsupportedEncodingException ignore) {
        throw new CertificateException("Unsupported encoding");
      }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}

