package com.jetbrains.python.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.packaging.ui.PyInstalledPackagesPanel;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyManagePackagesDialog extends DialogWrapper {
  private JPanel myMainPanel;

  public PyManagePackagesDialog(@NotNull final Project project, @NotNull Sdk sdk) {
    super(project, true);
    setTitle("Manage Python Packages");

    List<Sdk> sdks = PythonSdkType.getAllSdks();
    Collections.sort(sdks, new PreferredSdkComparator());
    final JComboBox sdkComboBox = new JComboBox(new CollectionComboBoxModel(sdks, sdk));
    sdkComboBox.setRenderer(new PySdkListCellRenderer());

    PackagesNotificationPanel notificationPanel = new PackagesNotificationPanel(project);
    final PyInstalledPackagesPanel packagesPanel = new PyInstalledPackagesPanel(project, notificationPanel);
    packagesPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    packagesPanel.updatePackages(new PyPackageManagementService(project, sdk));
    packagesPanel.updateNotifications(sdk);

    myMainPanel = new JPanel(new BorderLayout());
    final LabeledComponent<JComboBox> sdkLabeledComponent = LabeledComponent.create(sdkComboBox, "Interpreter:");
    sdkLabeledComponent.setLabelLocation(BorderLayout.WEST);
    myMainPanel.add(sdkLabeledComponent, BorderLayout.NORTH);
    myMainPanel.add(packagesPanel, BorderLayout.CENTER);
    myMainPanel.add(notificationPanel.getComponent(), BorderLayout.SOUTH);

    sdkComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Sdk sdk = (Sdk) sdkComboBox.getSelectedItem();
        packagesPanel.updatePackages(new PyPackageManagementService(project, sdk));
        packagesPanel.updateNotifications(sdk);
      }
    });

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "PyManagePackagesDialog";
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[0];
  }
}
