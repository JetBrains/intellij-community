package com.intellij.tasks.impl.ssl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Map;

import static com.intellij.openapi.util.Pair.create;

/**
 * @author Mikhail Golubev
 */
public class UntrustedCertificateWarningDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(UntrustedCertificateWarningDialog.class);
  private static DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
//  private static final String MESSAGE = "Server's certificate is untrusted and appears to be self-signed";

  private static final Map<String, String> FIELD_ABBREVIATIONS = ContainerUtil.newHashMap(
    create("CN", "Common Name"),
    create("O", "Organization"),
    create("OU", "Organizational Unit"),
    create("L", "Location"),
    create("C", "Country"),
    create("ST", "State")
  );

  private JPanel myRootPanel;
  private JLabel myWarningSign;
  private JPanel myIssuerInfoPanel;
  private JPanel mySubjectInfoPanel;
  private JPanel myValidityInfoPanel;
  private JTextPane myMessagePane;

  public UntrustedCertificateWarningDialog(X509Certificate certificate) {
    super((Project) null, false);

    setTitle("Untrusted Server's Certificate");
    setOKButtonText("Accept");
    setCancelButtonText("Reject");
    myWarningSign.setIcon(AllIcons.General.WarningDialog);


    fillPrincipalInfoPanel(myIssuerInfoPanel, certificate.getIssuerX500Principal());
    fillPrincipalInfoPanel(mySubjectInfoPanel, certificate.getSubjectX500Principal());

    myValidityInfoPanel.add(
      FormBuilder.createFormBuilder()
        .addLabeledComponent("Valid from", new JBLabel(DATE_FORMAT.format(certificate.getNotBefore())))
        .addLabeledComponent("Valid until", new JBLabel(DATE_FORMAT.format(certificate.getNotAfter())))
        .getPanel());

    init();
  }

  @Override
  protected void doOKAction() {
    LOG.debug(getSize().toString());
    super.doOKAction();
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void fillPrincipalInfoPanel(JPanel panel, X500Principal principal) {
    FormBuilder builder = FormBuilder.createFormBuilder();
    for (String field : principal.getName().split(",")) {
      field = field.trim();
      String[] parts = field.split("=", 2);
      if (parts.length != 2) {
        continue;
      }

      String name = parts[0];
      String value = parts[1];

      String longName = FIELD_ABBREVIATIONS.get(name.toUpperCase());
      if (longName == null) {
        continue;
      }

      builder = builder.addLabeledComponent(String.format("<html>%s (<b>%s</b>)</html>", longName, name), new JBLabel(value));
    }
    JPanel builderPanel = builder.getPanel();
    panel.add(builderPanel, BorderLayout.CENTER);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }
}
