package com.jetbrains.python.configuration;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages the SDK model shared between PythonSdkConfigurable and PyActiveSdkConfigurable.
 *
 * @author yole
 */
public class PyConfigurableInterpreterList {
  private ProjectSdksModel myModel;
  private JComboBox mySdkCombo;

  public void setSdkCombo(final JComboBox sdkCombo) {
    mySdkCombo = sdkCombo;
  }

  public void setSelectedSdk(final Sdk selectedSdk) {
    if (mySdkCombo != null)
      mySdkCombo.setSelectedItem(selectedSdk);
  }

  public static PyConfigurableInterpreterList getInstance(Project project) {
    return ServiceManager.getService(project, PyConfigurableInterpreterList.class);
  }

  public ProjectSdksModel getModel() {
    if (myModel == null) {
      myModel = new ProjectSdksModel();
      myModel.reset(null);
    }
    return myModel;
  }

  public void disposeModel() {
    if (myModel != null) {
      myModel.disposeUIResources();
      myModel = null;
    }
  }

  public List<Sdk> getAllPythonSdks() {
    List<Sdk> result = new ArrayList<Sdk>();
    for (Sdk sdk : getModel().getSdks()) {
      if (sdk.getSdkType() instanceof PythonSdkType) {
        result.add(sdk);
      }
    }
    Collections.sort(result, new Comparator<Sdk>() {
      @Override
      public int compare(Sdk o1, Sdk o2) {
        return Comparing.compare(o1.getName(), o2.getName());
      }
    });
    return result;
  }
}
