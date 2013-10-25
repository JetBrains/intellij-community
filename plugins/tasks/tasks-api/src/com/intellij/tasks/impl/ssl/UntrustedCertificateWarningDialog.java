package com.intellij.tasks.impl.ssl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
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
  private JPanel myCertificateInfoPanel;
  private JTextPane myNoticePane;
  private final X509Certificate myCertificate;
  private final String myPath, myPassword;

  public UntrustedCertificateWarningDialog(@NotNull X509Certificate certificate, @NotNull String storePath, @NotNull String password) {
    super((Project)null, false);

    myCertificate = certificate;
    myPath = FileUtil.toCanonicalPath(storePath);

    myPassword = password;

    FormBuilder builder = FormBuilder.createFormBuilder();

    // I'm not using separate panels and form builders to preserve alignment of labels
    builder = updateBuilderWithTitle(builder, "Issued To");
    builder = updateBuilderWithPrincipalData(builder, myCertificate.getSubjectX500Principal());
    builder = updateBuilderWithTitle(builder, "Issued By");
    builder = updateBuilderWithPrincipalData(builder, myCertificate.getIssuerX500Principal());
    builder = updateBuilderWithTitle(builder, "Validity Period");
    builder = builder
      .setIndent(IdeBorderFactory.TITLED_BORDER_INDENT)
      .addLabeledComponent("Valid from", new JBLabel(DATE_FORMAT.format(myCertificate.getNotBefore())))
      .addLabeledComponent("Valid until", new JBLabel(DATE_FORMAT.format(myCertificate.getNotAfter())));
    myCertificateInfoPanel.add(builder.getPanel(), BorderLayout.CENTER);

    setTitle("Untrusted Server's Certificate");
    setOKButtonText("Accept");
    setCancelButtonText("Reject");
    myWarningSign.setIcon(AllIcons.General.WarningDialog);

    Messages.installHyperlinkSupport(myNoticePane);
//    myNoticePane.setFont(myNoticePane.getFont().deriveFont((float)FontSize.SMALL.getSize()));
    myNoticePane.setText(
      String.format("<html><p><small>" +
                    "Accepted certificate will be saved in truststore <code>%s</code> with password <code>%s</code>" +
                    "</small></p><html>",
                    myPath, myPassword));

    init();
    LOG.debug("Preferred size: " + getPreferredSize());
  }

  @SuppressWarnings("MethodMayBeStatic")
  private FormBuilder updateBuilderWithPrincipalData(FormBuilder builder, X500Principal principal) {
    builder = builder.setIndent(IdeBorderFactory.TITLED_BORDER_INDENT);
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
    return builder.setIndent(0);
  }

  @SuppressWarnings("MethodMayBeStatic")
  private FormBuilder updateBuilderWithTitle(FormBuilder builder, String title) {
    return builder.addComponent(new TitledSeparator(title), IdeBorderFactory.TITLED_BORDER_TOP_INSET);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }
}
