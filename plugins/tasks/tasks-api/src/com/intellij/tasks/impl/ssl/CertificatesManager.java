package com.intellij.tasks.impl.ssl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code CertificatesManager} is responsible for negotiation SSL connection with server
 * that using trusted or self-signed certificates.
 *
 * @author Mikhail Golubev
 */
public class CertificatesManager {
  private static final Logger LOG = Logger.getInstance(CertificatesManager.class);
  private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];

  @NonNls private static final String DEFAULT_PATH = FileUtil.join(PathManager.getSystemPath(), "tasks", "cacerts");
  @NonNls private static final String DEFAULT_PASSWORD = "changeit";

  @NotNull
  public static CertificatesManager createDefault() {
    return createInstance(DEFAULT_PATH, DEFAULT_PASSWORD);
  }


  @NotNull
  public static CertificatesManager createInstance(@NotNull String cacertsPath, @NotNull String cacertsPassword) {
    return new CertificatesManager(cacertsPath, cacertsPassword);
  }

  private final String myCacertsPath;
  private final String myPassword;

  private CertificatesManager(@NotNull String cacertsPath, @NotNull String cacertsPassword) {
    myCacertsPath = cacertsPath;
    myPassword = cacertsPassword;
  }

  /**
   * Creates instance of {@code Protocol} for registration by {@link org.apache.commons.httpclient.protocol.Protocol#registerProtocol(String, org.apache.commons.httpclient.protocol.Protocol)}
   * <p/>
   * See {@link #createSslContext()} for details
   *
   * @return protocol instance
   */
  @Nullable
  public Protocol createProtocol() {
    try {
      final SSLSocketFactory factory = createSslContext().getSocketFactory();
      return new Protocol("https", new ProtocolSocketFactory() {
        @Override
        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
          throws IOException {
          return factory.createSocket(host, port, localAddress, localPort);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params)
          throws IOException {
          return createSocket(host, port, localAddress, localPort);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
          return factory.createSocket(host, port);
        }
      }, 443);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }


  /**
   * Creates special kind of {@code SSLContext} which X509TrustManager first checks certificate presence in
   * in default system-wide trust store (usually located at {@code ${JAVA_HOME}/lib/security/cacerts} or specified by
   * {@code javax.net.ssl.trustStore} property) and when in the one specified by first argument of factory method
   * {@link CertificatesManager#createInstance(String, String)}.
   * If certificate wasn't found in either, manager will ask user whether it can be
   * accepted (like web-browsers do) and if can, certificate will be added to specified trust store.
   * </p>
   * This method may be used for transition to HttpClient 4.x (see {@code HttpClients.custom().setSslContext(SSLContext)})
   *
   * @return instance of SSLContext with described behavior
   */
  @NotNull
  public SSLContext createSslContext() throws Exception {
    // SSLContext context = SSLContext.getDefault();
    SSLContext context = SSLContext.getInstance("TLS");
    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init((KeyStore)null);
    // assume that only X509 TrustManagers exist
    X509TrustManager systemManager = findX509TrustManager(factory.getTrustManagers());

    MutableX509TrustManager customManager = new MutableX509TrustManager(myCacertsPath, myPassword);
    MyX509TrustManager trustManager = new MyX509TrustManager(systemManager, customManager);
    // use default key store and secure random
    context.init(null, new TrustManager[]{trustManager}, null);
    return context;
  }


  private static class MyX509TrustManager implements X509TrustManager {
    private final X509TrustManager mySystemManager;
    private final MutableX509TrustManager myCustomManager;


    private MyX509TrustManager(X509TrustManager system, MutableX509TrustManager custom) {
      mySystemManager = system;
      myCustomManager = custom;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      // Not called by client
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] certificates, String s) throws CertificateException {
      try {
        mySystemManager.checkServerTrusted(certificates, s);
      }
      catch (CertificateException e1) {
        X509Certificate certificate = certificates[0];
        // looks like self-signed certificate
        if (certificates.length == 1 && certificateIsSelfSigned(certificate)) {
          // check-then-act sequence
          synchronized (myCustomManager) {
            try {
              myCustomManager.checkServerTrusted(certificates, s);
            }
            catch (CertificateException e2) {
              if (myCustomManager.isBroken() || !updateTrustStore(certificate)) {
                throw e1;
              }
            }
          }
        }
      }
    }

    private boolean updateTrustStore(final X509Certificate certificate) {
      Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
        myCustomManager.addCertificate(certificate);
        return true;
      }

      // can't use Application#invokeAndWait because of ReadAction
      final CountDownLatch proceeded = new CountDownLatch(1);
      final AtomicBoolean accepted = new AtomicBoolean();
      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          try {
            UntrustedCertificateWarningDialog dialog =
              new UntrustedCertificateWarningDialog(certificate, myCustomManager.myPath, myCustomManager.myPassword);
            accepted.set(dialog.showAndGet());
            if (accepted.get()) {
              LOG.debug("Certificate was accepted");
              myCustomManager.addCertificate(certificate);
            }
          }
          finally {
            proceeded.countDown();
          }
        }
      }, ModalityState.any());
      try {
        proceeded.await();
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      return accepted.get();
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return mySystemManager.getAcceptedIssuers();
    }
  }

  /**
   * Trust manager that supports addition of new certificates (most likely self-signed) to corresponding physical
   * key store.
   */
  private static class MutableX509TrustManager implements X509TrustManager {
    private final String myPath;
    private final String myPassword;
    private final TrustManagerFactory myFactory;
    private final KeyStore myKeyStore;
    private volatile X509TrustManager myTrustManager;
    private volatile boolean broken = false;

    private MutableX509TrustManager(@NotNull String path, @NotNull String password) {
      myPath = path;
      myPassword = password;
      myKeyStore = loadKeyStore(path, password);
      myFactory = createFactory();
      myTrustManager = initFactoryAndGetManager();
    }

    private TrustManagerFactory createFactory() {
      try {
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      }
      catch (NoSuchAlgorithmException e) {
        LOG.error(e);
        broken = true;
      }
      return null;
    }

    private KeyStore loadKeyStore(String path, String password) {
      KeyStore keyStore = null;
      try {
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        File cacertsFile = new File(path);
        if (cacertsFile.exists()) {
          FileInputStream stream = null;
          try {
            stream = new FileInputStream(path);
            keyStore.load(stream, password.toCharArray());
          }
          finally {
            StreamUtil.closeStream(stream);
          }
        }
        else {
          FileUtil.createParentDirs(cacertsFile);
          keyStore.load(null, password.toCharArray());
        }
      }
      catch (Exception e) {
        LOG.error(e);
        broken = true;
      }
      return keyStore;
    }

    public boolean addCertificate(X509Certificate certificate) {
      if (broken) {
        return false;
      }
      String alias = certificate.getIssuerX500Principal().getName();
      FileOutputStream stream = null;
      try {
        myKeyStore.setCertificateEntry(alias, certificate);
        stream = new FileOutputStream(myPath);
        myKeyStore.store(stream, myPassword.toCharArray());
        // trust manager should be updated each time its key store was modified
        myTrustManager = initFactoryAndGetManager();
        return true;
      }
      catch (Exception e) {
        LOG.error(e);
        return false;
      }
      finally {
        StreamUtil.closeStream(stream);
      }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      if (keyStoreIsEmpty() || broken) {
        throw new CertificateException();
      }
      myTrustManager.checkClientTrusted(certificates, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      if (keyStoreIsEmpty() || broken) {
        throw new CertificateException();
      }
      myTrustManager.checkServerTrusted(certificates, s);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      // trust no one if broken
      if (keyStoreIsEmpty() || broken) {
        return NO_CERTIFICATES;
      }
      return myTrustManager.getAcceptedIssuers();
    }

    private boolean keyStoreIsEmpty() {
      try {
        return myKeyStore.size() == 0;
      }
      catch (KeyStoreException e) {
        LOG.error(e);
        return true;
      }
    }

    private X509TrustManager initFactoryAndGetManager() {
      if (!broken) {
        try {
          myFactory.init(myKeyStore);
          return findX509TrustManager(myFactory.getTrustManagers());
        }
        catch (KeyStoreException e) {
          LOG.error(e);
          broken = true;
        }
      }
      return null;
    }

    public boolean isBroken() {
      return broken;
    }
  }

  private static X509TrustManager findX509TrustManager(TrustManager[] managers) {
    for (TrustManager manager : managers) {
      if (manager instanceof X509TrustManager) {
        return (X509TrustManager)manager;
      }
    }
    return null;
  }

  private static boolean certificateIsSelfSigned(X509Certificate certificate) {
    return certificate.getIssuerX500Principal().equals(certificate.getSubjectX500Principal());
  }
}
