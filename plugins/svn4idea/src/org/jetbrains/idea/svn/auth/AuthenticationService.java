// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IdeHttpClientHelpers;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.proxy.CommonProxy;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.jetbrains.idea.svn.SvnBundle.message;

public final class AuthenticationService {
  private static final Logger LOG = Logger.getInstance(AuthenticationService.class);

  private static final @NonNls String FATAL_HANDSHAKE_FAILURE_ERROR = "received fatal alert: handshake_failure";
  private static final @NonNls String SSL_V3_PROTOCOL = "SSLv3";
  private static final @NonNls String TLS_V1_PROTOCOL = "TLSv1";

  private static final @NonNls String TERMINAL_SSL_SERVER_AUTH_KIND = "terminal.ssl.server";

  private final @NotNull SvnVcs myVcs;
  private final boolean myIsActive;
  private boolean myProxyCredentialsWereReturned;
  private final @NotNull SvnConfiguration myConfiguration;
  private final Set<String> myRequestedCredentials;

  public AuthenticationService(@NotNull SvnVcs vcs, boolean isActive) {
    myVcs = vcs;
    myIsActive = isActive;
    myConfiguration = myVcs.getSvnConfiguration();
    myRequestedCredentials = new HashSet<>();
  }

  public @NotNull SvnVcs getVcs() {
    return myVcs;
  }

  public boolean isActive() {
    return myIsActive;
  }

  public @Nullable AuthenticationData requestCredentials(final Url repositoryUrl, final String type) {
    AuthenticationData authentication = null;

    if (repositoryUrl != null) {
      final String realm = repositoryUrl.toDecodedString();

      authentication = requestCredentials(realm, type, () -> myConfiguration.getInteractiveManager(myVcs).getProvider()
        .requestClientAuthentication(type, repositoryUrl, realm, true));
    }

    if (authentication == null) {
      LOG.warn("Could not get authentication. Type - " + type + ", Url - " + repositoryUrl);
    }

    return authentication;
  }

  private @Nullable <T> T requestCredentials(@NotNull String realm, @NotNull String type, @NotNull Supplier<T> fromUserProvider) {
    T result = null;
    // Search for stored credentials not only by key but also by "parent" keys. This is useful when we work just with URLs
    // (not working copy) and can't detect repository url beforehand because authentication is required. If found credentials of "parent"
    // are not correct then current key will already be stored in myRequestedCredentials - thus user will be asked for credentials and
    // provided result will be stored in cache (with necessary key).
    Object data = SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(type, realm);
    String key = SvnConfiguration.AuthStorage.getKey(type, realm);

    // we return credentials from cache if they are asked for the first time during command execution, otherwise - user is asked
    if (data != null && !myRequestedCredentials.contains(key)) {
      // we already have credentials in memory cache
      result = (T)data;
      myRequestedCredentials.add(key);
    }
    else if (myIsActive) {
      // ask user for credentials
      result = fromUserProvider.get();
      if (result != null) {
        // save user credentials to memory cache
        myConfiguration.acknowledge(type, realm, result);
        myRequestedCredentials.add(key);
      }
    }

    return result;
  }

  public @Nullable String requestSshCredentials(final @NotNull String realm,
                                                final @NotNull SimpleCredentialsDialog.Mode mode,
                                                final @NotNull String key) {
    return requestCredentials(realm, StringUtil.toLowerCase(mode.toString()), () -> {
      final Ref<String> answer = new Ref<>();

      Runnable command = () -> {
        SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myVcs.getProject());

        dialog.setup(mode, realm, key, true);
        dialog.setTitle(message("dialog.title.authentication.required"));
        dialog.setSaveEnabled(false);
        if (dialog.showAndGet()) {
          answer.set(dialog.getPassword());
        }
      };

      // Use ModalityState.any() as currently ssh credentials in terminal mode are requested in the thread that reads output and not in
      // the thread that started progress
      WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(command, ModalityState.any());

      return answer.get();
    });
  }

  public @NotNull AcceptResult acceptCertificate(final @NotNull Url url, final @NotNull String certificateInfo) {
    // TODO: Probably explicitly construct server url for realm here - like in CertificateTrustManager.
    String realm = url.toDecodedString();
    Object data = SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(TERMINAL_SSL_SERVER_AUTH_KIND, realm);
    AcceptResult result;

    if (data != null) {
      result = (AcceptResult)data;
    }
    else {
      result = getAuthenticationManager().getProvider().acceptServerAuthentication(url, realm, certificateInfo, true);

      if (!AcceptResult.REJECTED.equals(result)) {
        myConfiguration.acknowledge(TERMINAL_SSL_SERVER_AUTH_KIND, realm, result);
      }
    }

    return result;
  }

  public boolean acceptSSLServerCertificate(@Nullable Url repositoryUrl) throws SvnBindException {
    if (repositoryUrl == null) {
      return false;
    }

    HttpClient client = getClient(repositoryUrl);
    try {
      client.execute(new HttpGet(repositoryUrl.toDecodedString()));
      return true;
    }
    catch (IOException e) {
      throw new SvnBindException(fixMessage(e), e);
    }
  }

  private static @Nls @Nullable String fixMessage(@NotNull IOException e) {
    String message = null;

    if (e instanceof SSLHandshakeException) {
      if (StringUtil.containsIgnoreCase(e.getMessage(), FATAL_HANDSHAKE_FAILURE_ERROR)) {
        message = e.getMessage() + ". " + message("label.specify.ssl.protocol.manually");
      }
      else if (e.getCause() != null) {
        // SSLHandshakeException.getMessage() could contain full type name of cause exception - for instance when cause is
        // CertificateException. We just use cause exception message not to show exception type to the user.
        message = e.getCause().getMessage();
      }
    }

    return message;
  }

  private @NotNull HttpClient getClient(@NotNull Url repositoryUrl) {
    // TODO: Implement algorithm of resolving necessary enabled protocols (TLSv1 vs SSLv3) instead of just using values from Settings.
    SSLContext sslContext = createSslContext(repositoryUrl);
    List<String> supportedProtocols = getSupportedSslProtocols();
    SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext, ArrayUtilRt.toStringArray(supportedProtocols), null,
                                                                              SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    // TODO: Seems more suitable here to read timeout values directly from config file - without utilizing SvnAuthenticationManager.
    final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    if (haveDataForTmpConfig()) {
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyIfEnabled(requestConfigBuilder);
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyCredentialsIfEnabled(credentialsProvider);
    }

    return HttpClients.custom()
      .setSSLSocketFactory(socketFactory)
      .setDefaultSocketConfig(SocketConfig.custom()
                                .setSoTimeout(getAuthenticationManager().getReadTimeout(repositoryUrl))
                                .build())
      .setDefaultRequestConfig(requestConfigBuilder
                                 .setConnectTimeout(getAuthenticationManager().getConnectTimeout(repositoryUrl))
                                 .build())
      .setDefaultCredentialsProvider(credentialsProvider)
      .build();
  }

  private @NotNull List<String> getSupportedSslProtocols() {
    List<String> result = new ArrayList<>();

    switch (myConfiguration.getSslProtocols()) {
      case sslv3:
        result.add(SSL_V3_PROTOCOL);
        break;
      case tlsv1:
        result.add(TLS_V1_PROTOCOL);
        break;
      case all:
        break;
    }

    return result;
  }

  private @NotNull SSLContext createSslContext(@NotNull Url url) {
    SSLContext result = CertificateManager.getSystemSslContext();
    TrustManager trustManager = new CertificateTrustManager(this, url);

    try {
      result.init(CertificateManager.getDefaultKeyManagers(), new TrustManager[]{trustManager}, null);
    }
    catch (KeyManagementException e) {
      LOG.error(e);
    }

    return result;
  }

  public @NotNull SvnAuthenticationManager getAuthenticationManager() {
    return isActive() ? myConfiguration.getInteractiveManager(myVcs) : myConfiguration.getPassiveAuthenticationManager(myVcs);
  }

  // TODO: rename
  public boolean haveDataForTmpConfig() {
    final HttpConfigurable instance = HttpConfigurable.getInstance();
    return myConfiguration.isUseDefaultProxy() && (instance.USE_HTTP_PROXY || instance.USE_PROXY_PAC);
  }

  public static @Nullable Proxy getIdeaDefinedProxy(final @NotNull Url url) {
    // TODO: Check if removeNoProxy() is still needed
    // SVNKit authentication implementation sets repositories as noProxy() to provide custom proxy authentication logic - see for instance,
    // SvnAuthenticationManager.getProxyManager(). But noProxy() setting is not cleared correctly in all cases - so if svn command
    // (for command line) is executed on thread where repository url was added as noProxy() => proxies are not retrieved for such commands
    // and execution logic is incorrect.

    // To prevent such behavior repositoryUrl is manually removed from noProxy() list (for current thread).
    // NOTE, that current method is only called from code flows for executing commands through command line client and should not be called
    // from SVNKit code flows.
    CommonProxy.getInstance().removeNoProxy(url.getProtocol(), url.getHost(), url.getPort());

    final List<Proxy> proxies = CommonProxy.getInstance().select(URI.create(url.toString()));
    if (!proxies.isEmpty()) {
      for (Proxy proxy : proxies) {
        if (HttpConfigurable.isRealProxy(proxy) && Proxy.Type.HTTP.equals(proxy.type())) {
          return proxy;
        }
      }
    }
    return null;
  }

  public @Nullable PasswordAuthentication getProxyAuthentication(@NotNull Url repositoryUrl) {
    Proxy proxy = getIdeaDefinedProxy(repositoryUrl);
    PasswordAuthentication result = null;

    if (proxy != null) {
      if (myProxyCredentialsWereReturned) {
        showFailedAuthenticateProxy();
      }
      else {
        result = getProxyAuthentication(proxy, repositoryUrl);
        myProxyCredentialsWereReturned = result != null;
      }
    }

    return result;
  }

  private static void showFailedAuthenticateProxy() {
    HttpConfigurable instance = HttpConfigurable.getInstance();
    String message = instance.USE_HTTP_PROXY || instance.USE_PROXY_PAC
                     ? message("popup.content.failed.to.authenticate.to.proxy.change.credentials")
                     : message("popup.content.failed.to.authenticate.to.proxy");

    PopupUtil.showBalloonForActiveComponent(message, MessageType.ERROR);
  }

  private static @Nullable PasswordAuthentication getProxyAuthentication(@NotNull Proxy proxy, @NotNull Url repositoryUrl) {
    PasswordAuthentication result = null;

    try {
      result = Authenticator.requestPasswordAuthentication(repositoryUrl.getHost(), ((InetSocketAddress)proxy.address()).getAddress(),
                                                           repositoryUrl.getPort(), repositoryUrl.getProtocol(), repositoryUrl.getHost(),
                                                           repositoryUrl.getProtocol(), new URL(repositoryUrl.toString()),
                                                           Authenticator.RequestorType.PROXY);
    }
    catch (MalformedURLException e) {
      LOG.info(e);
    }

    return result;
  }

  public void reset() {
  }

  public @NotNull Path getSpecialConfigDir() {
    return myConfiguration.getConfigurationPath();
  }
}
