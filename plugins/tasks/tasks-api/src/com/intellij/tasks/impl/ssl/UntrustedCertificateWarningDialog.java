package com.intellij.tasks.impl.ssl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Map;

import static com.intellij.openapi.util.Pair.create;

/**
 * @author Mikhail Golubev
 */
public class UntrustedCertificateWarningDialog extends DialogWrapper {
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
  private JBTable myIssuerInfo;
  private JBTable mySubjectInfo;
  private JBTable myValidityInfo;
  private JLabel myWarningSign;

  public UntrustedCertificateWarningDialog(Project project, X509Certificate certificate) {
    super(project, false);

    setTitle("Untrusted Server's Certificate");
    setOKButtonText("Accept");
    setCancelButtonText("Reject");
    myWarningSign.setIcon(AllIcons.General.WarningDialog);

    fillTable(myIssuerInfo, certificate.getIssuerX500Principal());
    fillTable(mySubjectInfo, certificate.getSubjectX500Principal());

    @SuppressWarnings("unchecked")
    ListTableModel<Field> model = (ListTableModel<Field>)myValidityInfo.getModel();
    model.addRow(new Field("Valid from", DATE_FORMAT.format(certificate.getNotBefore())));
    model.addRow(new Field("Valid until", DATE_FORMAT.format(certificate.getNotAfter())));

    init();
  }

  private static void fillTable(JBTable table, X500Principal principal) {
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

      @SuppressWarnings("unchecked")
      ListTableModel<Field> model = (ListTableModel<Field>)table.getModel();
      model.addRow(new Field(String.format("%s (%s)", longName, name), value));
    }
  }


  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }


  private void createUIComponents() {
    myIssuerInfo = createTable();
    mySubjectInfo = createTable();
    myValidityInfo = createTable();
  }

  private static JBTable createTable() {
    return new JBTable(new ListTableModel<Field>(
      new ColumnInfo<Field, String>("Field") {
        @Nullable
        @Override
        public String valueOf(Field field) {
          return field.getName();
        }

        @Nullable
        @Override
        public String getPreferredStringValue() {
          return "###############################";
        }


      },
      new ColumnInfo<Field, String>("Value") {
        @Nullable
        @Override
        public String valueOf(Field field) {
          return field.getValue();
        }

        @Nullable
        @Override
        public String getPreferredStringValue() {
          return "###############################";
        }
      }
    ));
  }

  private static class Field {
    private final String myName, myValue;

    private Field(String name, String value) {
      myName = name;
      myValue = value;
    }

    public String getName() {
      return myName;
    }

    public String getValue() {
      return myValue;
    }
  }
}
