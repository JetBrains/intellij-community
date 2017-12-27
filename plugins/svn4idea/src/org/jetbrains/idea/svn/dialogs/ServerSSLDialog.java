// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.net.ssl.CertificateInfoPanel;
import com.intellij.util.net.ssl.CertificateWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.auth.AcceptResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.security.cert.X509Certificate;

public class ServerSSLDialog extends DialogWrapper {

  @NotNull private final String myCertificateInfo;
  private Action myTempAction;
  private AcceptResult myResult;

  public ServerSSLDialog(final Project project, @NotNull X509Certificate cert, boolean store) {
    this(project, getServerCertificateInfo(cert), store);
  }

  public ServerSSLDialog(final Project project, @NotNull String certificateInfo, boolean store) {
    super(project, true);
    myCertificateInfo = certificateInfo;
    myResult = AcceptResult.REJECTED;
    setOKButtonText(SvnBundle.message("button.text.ssl.accept"));
    setOKActionEnabled(store);
    setCancelButtonText(SvnBundle.message("button.text.ssl.reject"));
    setTitle(SvnBundle.message("dialog.title.ssl.examine.server.crertificate"));
    setResizable(true);
    init();
  }

  public boolean shouldCloseOnCross() {
    return false;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getTempAction(), getCancelAction()};
  }

  private Action getTempAction() {
    if (myTempAction == null) {
      myTempAction = new AbstractAction(SvnBundle.message("server.ssl.accept.temporary.action.name")) {
        public void actionPerformed(ActionEvent e) {
          myResult = AcceptResult.ACCEPTED_TEMPORARILY;
          close(0);
        }
      };
    }
    return myTempAction;
  }

  protected void doOKAction() {
    myResult = AcceptResult.ACCEPTED_PERMANENTLY;
    super.doOKAction();
  }

  public void doCancelAction() {
    myResult = AcceptResult.REJECTED;
    super.doCancelAction();
  }

  public AcceptResult getResult() {
    return myResult;
  }

  protected String getDimensionServiceKey() {
    return "svn.sslDialog";
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(5,5));
    panel.add(new JLabel(SvnBundle.message("label.ssl.server.provided.certificate")), BorderLayout.NORTH);
    JTextArea area = new JTextArea(5, 50);
    area.setText(myCertificateInfo);
    area.setEditable(false);
    panel.add(ScrollPaneFactory.createScrollPane(area), BorderLayout.CENTER);

    return panel;
  }

  @SuppressWarnings({"HardCodedStringLiteral", "StringBufferReplaceableByString"})
  @NotNull
  private static String getServerCertificateInfo(@NotNull X509Certificate cert) {
    return new StringBuilder()
      .append(" - Subject: ")
      .append(cert.getSubjectDN().getName())
      .append('\n')
      .append(" - Valid: ")
      .append("from ").append(cert.getNotBefore()).append(" until ").append(cert.getNotAfter())
      .append('\n')
      .append(" - Issuer: ")
      .append(cert.getIssuerDN().getName())
      .append('\n')
      .append(" - Fingerprint: ")
      .append(CertificateInfoPanel.formatHex(new CertificateWrapper(cert).getSha1Fingerprint(), false))
      .toString();
  }
}
