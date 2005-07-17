package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;
import java.awt.*;
import java.awt.event.ActionEvent;

import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 17.07.2005
 * Time: 19:35:02
 * To change this template use File | Settings | File Templates.
 */
public class ServerSSLDialog extends DialogWrapper {

  private X509Certificate myCertificate;
  private Action myTempAction;
  private int myResult;

  protected ServerSSLDialog(final Project project, X509Certificate cert, boolean store) {
    super(project, true);
    myCertificate = cert;
    myResult = ISVNAuthenticationProvider.REJECTED;
    setOKButtonText("_Accept");
    setOKActionEnabled(store);
    setCancelButtonText("_Reject");
    setTitle("Examine Server Crertificate");
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
      myTempAction = new AbstractAction("Accept _Temporary") {
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
    panel.add(new JLabel("Server provided the following certificate:"), BorderLayout.NORTH);
    JTextArea area = new JTextArea(5, 50);
    area.setText(getServerCertificateInfo(myCertificate));
    area.setEditable(false);
    panel.add(new JScrollPane(area), BorderLayout.CENTER);

    return panel;
  }

  private static String getFingerprint(X509Certificate cert) {
        StringBuffer s = new StringBuffer();
        try  {
           MessageDigest md = MessageDigest.getInstance("SHA1");
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
