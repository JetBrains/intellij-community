/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * @author alex
 */
public class ServerSSLDialog extends DialogWrapper {

  @NotNull private final String myCertificateInfo;
  private Action myTempAction;
  private int myResult;
  @NonNls public static final String ALGORITHM_SHA1 = "SHA1";

  public ServerSSLDialog(final Project project, @NotNull X509Certificate cert, boolean store) {
    this(project, getServerCertificateInfo(cert), store);
  }

  public ServerSSLDialog(final Project project, @NotNull String certificateInfo, boolean store) {
    super(project, true);
    myCertificateInfo = certificateInfo;
    myResult = ISVNAuthenticationProvider.REJECTED;
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
          myResult = ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
          close(0);
        }
      };
    }
    return myTempAction;
  }

  protected void doOKAction() {
    myResult = ISVNAuthenticationProvider.ACCEPTED;
    super.doOKAction();
  }

  public void doCancelAction() {
    myResult = ISVNAuthenticationProvider.REJECTED;
    super.doCancelAction();
  }

  public int getResult() {
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

  @NotNull
  private static String getFingerprint(@NotNull X509Certificate cert) {
    byte[] data = null;

    try {
      data = cert.getEncoded();
    }
    catch (CertificateEncodingException ignore) {
    }

    return data != null ? SVNSSLUtil.getFingerprint(data, ALGORITHM_SHA1) : "";
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
      .append(getFingerprint(cert))
      .toString();
  }
}
