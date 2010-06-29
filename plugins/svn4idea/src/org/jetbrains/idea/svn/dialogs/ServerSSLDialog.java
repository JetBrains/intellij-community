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
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

/**
 * @author alex
 */
public class ServerSSLDialog extends DialogWrapper {

  private final X509Certificate myCertificate;
  private Action myTempAction;
  private int myResult;
  @NonNls public static final String ALGORITHM_SHA1 = "SHA1";

  protected ServerSSLDialog(final Project project, X509Certificate cert, boolean store) {
    super(project, true);
    myCertificate = cert;
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
    area.setText(getServerCertificateInfo(myCertificate));
    area.setEditable(false);
    panel.add(new JBScrollPane(area), BorderLayout.CENTER);

    return panel;
  }

  private static String getFingerprint(X509Certificate cert) {
        StringBuffer s = new StringBuffer();
        try  {
           MessageDigest md = MessageDigest.getInstance(ALGORITHM_SHA1);
           md.update(cert.getEncoded());
           byte[] digest = md.digest();
           for (int i= 0; i < digest.length; i++)  {
              if (i != 0) {
                  s.append(':');
              }
              int b = digest[i] & 0xFF;
              String hex = Integer.toHexString(b);
              if (hex.length() == 1) {
                  s.append('0');
              }
              s.append(hex.toLowerCase());
           }
        } catch (Exception e)  {
          //
        }
        return s.toString();
     }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getServerCertificateInfo(X509Certificate cert) {
      StringBuffer info = new StringBuffer();
      info.append(" - Subject: ");
      info.append(cert.getSubjectDN().getName());
      info.append('\n');
      info.append(" - Valid: ");
      info.append("from ").append(cert.getNotBefore()).append(" until ").append(cert.getNotAfter());
      info.append('\n');
      info.append(" - Issuer: ");
      info.append(cert.getIssuerDN().getName());
      info.append('\n');
      info.append(" - Fingerprint: ");
      info.append(getFingerprint(cert));
      return info.toString();
  }
}
