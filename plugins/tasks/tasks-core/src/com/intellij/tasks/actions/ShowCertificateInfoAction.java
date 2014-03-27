package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.net.ssl.CertificateWarningDialog;

import java.security.cert.X509Certificate;
import java.util.List;

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
      CertificateManager manager = CertificateManager.getInstance();
      List<X509Certificate> certificates = manager.getCustomTrustManager().getCertificates();
      if (certificates.isEmpty()) {
        Messages.showInfoMessage(String.format("Key store '%s' is empty", manager.getCacertsPath()), "No Certificates Available");
      } else {
        CertificateWarningDialog dialog = CertificateWarningDialog.createUntrustedCertificateWarning(certificates.get(0));
        LOG.debug("Accepted: " + dialog.showAndGet());
      }
    }
    catch (Exception logged) {
      LOG.error(logged);
    }
  }
}

