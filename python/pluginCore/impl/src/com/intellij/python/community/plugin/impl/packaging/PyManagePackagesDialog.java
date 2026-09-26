// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageManagers;
import com.jetbrains.python.packaging.PyPackagesNotificationPanel;
import com.jetbrains.python.packaging.ui.PyInstalledPackagesPanel;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.common.PyInterpreterItem;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PySdkRenderingKt;
import com.intellij.python.sdk.backend.PySdkBundle;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;


final class PyManagePackagesDialog extends DialogWrapper {
  private final JPanel myMainPanel;

  PyManagePackagesDialog(final @NotNull Project project, @NotNull Sdk sdk) {
    super(project, true);
    setTitle(PyBundle.message("manage.python.packages"));

    List<Sdk> sdks = new ArrayList<>(ContainerUtil.sorted(PythonSdkUtil.getAllSdks(), new PreferredSdkComparator()));
    // The combo holds items, not SDKs: a row states whether its interpreter can be used, which takes running it.
    List<PyInterpreterItem> items = PySdkRenderingKt.interpreterItemsUnderProgress(sdks, project);
    PyInterpreterItem selected = null;
    for (int i = 0; i < sdks.size(); i++) {
      if (sdks.get(i).equals(sdk)) selected = items.get(i);
    }
    final JComboBox sdkComboBox = new JComboBox(new CollectionComboBoxModel(items, selected));
    sdkComboBox.setRenderer(new PySdkListCellRenderer());

    PackagesNotificationPanel notificationPanel = new PyPackagesNotificationPanel();
    final PyInstalledPackagesPanel packagesPanel = new PyInstalledPackagesPanel(project, notificationPanel);
    packagesPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    packagesPanel.updatePackages(PyPackageManagers.getInstance().getManagementService(project, sdk));
    packagesPanel.updateNotifications(sdk);

    myMainPanel = new JPanel(new BorderLayout());
    final LabeledComponent<JComboBox> sdkLabeledComponent = LabeledComponent.create(sdkComboBox, PySdkBundle.message("python.interpreter.label"));
    sdkLabeledComponent.setLabelLocation(BorderLayout.WEST);
    myMainPanel.add(sdkLabeledComponent, BorderLayout.NORTH);
    myMainPanel.add(packagesPanel, BorderLayout.CENTER);
    myMainPanel.add(notificationPanel.getComponent(), BorderLayout.SOUTH);

    sdkComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // The combo holds items, not SDKs. An item can outlive the interpreter it names, so this may be null.
        Sdk selected = sdkComboBox.getSelectedItem() instanceof PyInterpreterItem item ? PythonInterpreterExtKt.findSdk(item) : null;
        packagesPanel.updatePackages(PyPackageManagers.getInstance().getManagementService(project, selected));
        packagesPanel.updateNotifications(selected);
      }
    });

    init();
    myMainPanel.setPreferredSize(new Dimension(JBUIScale.scale(900), JBUIScale.scale(700)));
    myMainPanel.setMinimumSize(new Dimension(JBUIScale.scale(900), JBUIScale.scale(700)));
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "PyManagePackagesDialog";
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[0];
  }
}
