// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;


class PythonSdkComboBox extends ComboboxWithBrowseButton {
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
    sdkList.add(0, null);
    getComboBox().setModel(new DefaultComboBoxModel(sdkList.toArray(new Sdk[0])));
    getComboBox().setSelectedItem(sdkToSelect);
  }

  public void updateSdkList() {
    updateSdkList((Sdk) getComboBox().getSelectedItem(), false);
  }

  public Sdk getSelectedSdk() {
    return (Sdk) getComboBox().getSelectedItem();
  }
}
