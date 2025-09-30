// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.security.ssl;

import org.jetbrains.idea.maven.server.MavenServerGlobals;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SslIDEConfirmingTrustStore {

  public static final String CHECK_CLIENT_TRUSTED = "----------checkClientTrusted----------";
  public static final String CHECK_SERVER_TRUSTED = "----------checkServerTrusted----------";
  public static final String IDE_DELEGATE_TRUST_MANAGER = "----------IdeDelegateTrustManager----------";
  public static final String DELEGATE_RESPONSE = "----------RESPONSE----------";
  public static final String DELEGATE_RESPONSE_OK = "----------OK----------";
  public static final String DELEGATE_RESPONSE_ERROR = "----------ERROR----------";


  private static final Multiplexor ourMultiplexor = new Multiplexor();
  public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
  public static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

  public static void setup() {
    if (System.getProperty("javax.net.ssl.keyStore") != null) {
      MavenServerGlobals.getLogger().warn("Will not delegate SSL certificate management to IDE, " +
                                          "looks like client certificate authentication is required");
      return;
    }
    try {
      startMultiplexorThread();
      TrustManager delegateTM = new IdeDelegateTrustManager();

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[]{delegateTM}, null);

      SSLContext.setDefault(sslContext);
    }
    catch (Throwable e) {
      MavenServerGlobals.getLogger().error(e);
    }
  }

  private static void startMultiplexorThread() {
    ourMultiplexor.start();
  }

  private static class TrustRequestKey {
    private final X509Certificate[] myChain;
    private final String myType;

    TrustRequestKey(X509Certificate[] chain, String authType) {
      myChain = chain;
      myType = authType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TrustRequestKey)) return false;
      TrustRequestKey key = (TrustRequestKey)o;
      return Objects.deepEquals(myChain, key.myChain) && Objects.equals(myType, key.myType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(Arrays.hashCode(myChain), myType);
    }
  }

  private static class IdeDelegateTrustManager implements X509TrustManager {
    private ConcurrentHashMap<TrustRequestKey, Boolean> succeed = new ConcurrentHashMap<>();
    private final List<X509TrustManager> myTrustManagers;

    IdeDelegateTrustManager() throws NoSuchAlgorithmException, KeyStoreException {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore)null);
      myTrustManagers = new ArrayList<>();

      for (TrustManager m : trustManagerFactory.getTrustManagers()) {
        if (m instanceof X509TrustManager) {
          myTrustManagers.add((X509TrustManager)m);
        }
      }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      for (X509TrustManager m : myTrustManagers) {
        try {
          m.checkClientTrusted(chain, authType);
          return;
        }
        catch (CertificateException ignore) {
        }
      }
      throw new CertificateException();
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private void sendAndWaitForResponse(String out, Integer key) throws CertificateException {
      synchronized (this) {
        System.out.println(out);
      }
      ourMultiplexor.waitForResponse(key);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      for (X509TrustManager m : myTrustManagers) {
        try {
          m.checkServerTrusted(chain, authType);
          return;
        }
        catch (CertificateException ignore) {
        }
      }
      TrustRequestKey key = new TrustRequestKey(chain, authType);
      Boolean cached = succeed.get(key);
      if (cached != null && cached) {
        return;
      }
      doCheckTrusted(chain, authType, CHECK_SERVER_TRUSTED);
      succeed.put(key, Boolean.TRUE);
    }

    private void doCheckTrusted(X509Certificate[] chain, String authType, String methodSignature) throws CertificateException {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      Integer key = ourMultiplexor.getKey();
      try (PrintStream out = new PrintStream(os, false, "ISO-8859-1")) {
        out.println(IDE_DELEGATE_TRUST_MANAGER);
        out.println(methodSignature);
        out.println(key);
        out.println(chain.length);
        out.println(authType);

        for (X509Certificate certificate : chain) {
          out.println(BEGIN_CERTIFICATE);

          try {
            out.println(Base64.getMimeEncoder(80, "\n".getBytes(StandardCharsets.US_ASCII))
                          .encodeToString(certificate.getEncoded()));
          }
          catch (CertificateEncodingException e) {
            out.println("#ERROR: " + e.getMessage());
          }
          out.println(END_CERTIFICATE);
        }
        out.println(methodSignature);
        sendAndWaitForResponse(os.toString("ISO-8859-1"), key);
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

