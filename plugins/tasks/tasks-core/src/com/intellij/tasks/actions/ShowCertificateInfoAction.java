package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.net.ssl.CertificateWarningDialog;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * @author Mikhail Golubev
 */
public class ShowCertificateInfoAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(ShowCertificateInfoAction.class);

  public ShowCertificateInfoAction() {
    super("Show certificate information dialog");
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    try {
      InputStream stream = ShowCertificateInfoAction.class.getResourceAsStream("keystore");
      try {
        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(stream, "changeit".toCharArray());
        X509Certificate certificate = (X509Certificate)keyStore.getCertificate("mykey");
        CertificateWarningDialog dialog = CertificateWarningDialog.createUntrustedCertificateWarning(certificate);
        LOG.debug("Accepted: " + dialog.showAndGet());
      }
      finally {
        StreamUtil.closeStream(stream);
      }
    }
    catch (Exception logged) {
      LOG.error(logged);
    }
  }
}

