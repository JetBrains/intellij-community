// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class PythonSdkEditorTab extends FacetEditorTab {
  private JPanel myMainPanel;
  private PythonSdkComboBox mySdkComboBox;
  private final FacetEditorContext myEditorContext;
  private final MessageBusConnection myConnection;

  public PythonSdkEditorTab(final FacetEditorContext editorContext) {
    myEditorContext = editorContext;
    final Project project = editorContext.getProject();
    mySdkComboBox.setProject(project);
    myConnection = project.getMessageBus().connect();
    myConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      @Override
      public void jdkAdded(@NotNull Sdk jdk) {
        mySdkComboBox.updateSdkList();
      }

      @Override
      public void jdkRemoved(@NotNull Sdk jdk) {
        mySdkComboBox.updateSdkList();
      }

      @Override
      public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
        mySdkComboBox.updateSdkList();
      }
    });
  }

  @Override
  @Nls
  public String getDisplayName() {
    return PyBundle.message("configurable.PythonSdkEditorTab.display.name");
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return mySdkComboBox.getSelectedSdk() != getFacetConfiguration().getSdk();
  }

  private JavaPythonFacetConfiguration getFacetConfiguration() {
    return ((JavaPythonFacetConfiguration) myEditorContext.getFacet().getConfiguration());
  }

  @Override
  public void apply() {
    getFacetConfiguration().setSdk(mySdkComboBox.getSelectedSdk());
  }

  @Override
  public void reset() {
    mySdkComboBox.updateSdkList(getFacetConfiguration().getSdk(), false);
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myConnection);
  }
}
