// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.net.ssl.ClientOnlyTrustManager;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.api.Url;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * We assume that this trust manager is only used when server certificate is valid but untrusted. So we do not perform any additional
 * validation here - just checking if certificate is trusted in several ways:
 * - runtime cache
 * - java trust store
 * - "Server Certificates" settings
 * - ask user
 *
 * @author Konstantin Kolosovsky.
 */
public class CertificateTrustManager extends ClientOnlyTrustManager {

  private static final Logger LOG = Logger.getInstance(CertificateTrustManager.class);

  private static final String CMD_SSL_SERVER = "cmd.ssl.server";

  @NotNull private final AuthenticationService myAuthenticationService;
  @NotNull private final Url myRepositoryUrl;
  @NotNull private final String myRealm;

  public CertificateTrustManager(@NotNull AuthenticationService authenticationService, @NotNull Url repositoryUrl) {
    myAuthenticationService = authenticationService;
    myRepositoryUrl = repositoryUrl;
    myRealm = new URIBuilder()
      .setScheme(repositoryUrl.getProtocol())
      .setHost(repositoryUrl.getHost())
      .setPort(repositoryUrl.getPort())
      .toString();
  }

  @Override
  public void checkServerTrusted(@Nullable X509Certificate[] chain, String authType) throws CertificateException {
    if (chain != null && chain.length > 0 && chain[0] != null) {
      X509Certificate certificate = chain[0];

      if (!checkPassive(certificate)) {
        if (!isAcceptedByIdea(chain, authType)) {
          checkActive(certificate);
        }

        // no exceptions - so certificate is trusted - save to runtime cache
        acknowledge(certificate);
      }
    }
  }

  private boolean checkPassive(@NotNull X509Certificate certificate) {
    Object cachedData = SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(CMD_SSL_SERVER, myRealm);

    return certificate.equals(cachedData);
  }

  private static boolean isAcceptedByIdea(@NotNull X509Certificate[] chain, String authType) {
    boolean result;

    try {
      CertificateManager.getInstance().getTrustManager().checkServerTrusted(chain, authType, false, false);
      result = true;
    }
    catch (CertificateException e) {
      LOG.debug(e);
      result = false;
    }

    return result;
  }

  private void checkActive(@NotNull X509Certificate certificate) throws CertificateException {
    boolean isStorageEnabled = myAuthenticationService.getAuthenticationManager().getHostOptions(myRepositoryUrl).isAuthStorageEnabled();
    AcceptResult result = myAuthenticationService.getAuthenticationManager().getProvider()
      .acceptServerAuthentication(myRepositoryUrl, myRealm, certificate, isStorageEnabled);

    switch (result) {
      case ACCEPTED_PERMANENTLY:
        // TODO: --trust-server-cert command line key does not allow caching credentials permanently - so permanent caching should be
        // TODO: separately implemented. Try utilizing "Server Certificates" settings for this.
      case ACCEPTED_TEMPORARILY:
        // acknowledge() is called in checkServerTrusted()
        break;
      case REJECTED:
        throw new CertificateException("Server SSL certificate rejected");
    }
  }

  private void acknowledge(@NotNull X509Certificate certificate) {
    myAuthenticationService.getVcs().getSvnConfiguration().acknowledge(CMD_SSL_SERVER, myRealm, certificate);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return CertificateManager.getInstance().getTrustManager().getAcceptedIssuers();
  }
}
