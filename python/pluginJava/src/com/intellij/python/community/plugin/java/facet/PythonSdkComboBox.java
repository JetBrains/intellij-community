// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.python.sdk.common.PyInterpreterItem;
import com.intellij.python.sdk.common.PyInterpreterRef;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PySdkRenderingKt;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.swing.DefaultComboBoxModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;


final class PythonSdkComboBox extends ComboboxWithBrowseButton {
  private Project myProject;

  PythonSdkComboBox() {
    getComboBox().setRenderer(new PySdkListCellRenderer());
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Sdk selectedSdk = getSelectedSdk();
        final Project project = myProject != null ? myProject : ProjectManager.getInstance().getDefaultProject();
        ProjectJdksEditor editor = new ProjectJdksEditor(selectedSdk, project, PythonSdkComboBox.this);
        if (editor.showAndGet()) {
          selectedSdk = editor.getSelectedJdk();
          updateSdkList(selectedSdk, false);
        }
      }
    });
    updateSdkList(null, true);
  }

  public void setProject(Project project) {
    myProject = project;
  }

  public void updateSdkList(Sdk sdkToSelect, boolean selectAnySdk) {
    final List<Sdk> sdkList = new ArrayList<>(ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance()));
    if (selectAnySdk && !sdkList.isEmpty()) {
      sdkToSelect = sdkList.get(0);
    }
    // The combo holds items, not SDKs: a row states whether its interpreter can be used, which takes running it.
    final List<PyInterpreterItem> items = new ArrayList<>(PySdkRenderingKt.interpreterItemsUnderProgress(sdkList, this));
    final PyInterpreterItem toSelect = itemFor(items, sdkToSelect);
    items.addFirst(null);
    getComboBox().setModel(new DefaultComboBoxModel(items.toArray(new PyInterpreterItem[0])));
    getComboBox().setSelectedItem(toSelect);
  }

  public void updateSdkList() {
    updateSdkList(getSelectedSdk(), false);
  }

  public Sdk getSelectedSdk() {
    return getComboBox().getSelectedItem() instanceof PyInterpreterItem item ? PythonInterpreterExtKt.findSdk(item) : null;
  }

  /** The row that stands for {@code sdk}, or null when the list holds none for it. */
  private static PyInterpreterItem itemFor(List<PyInterpreterItem> items, Sdk sdk) {
    if (sdk == null) return null;
    PyInterpreterRef ref = PythonInterpreterExtKt.asInterpreterRef(sdk);
    return ContainerUtil.find(items, item -> ref.equals(item.getRef()));
  }
}
