package com.jetbrains.python.facet;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author yole
 */
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
      public void jdkAdded(Sdk jdk) {
        mySdkComboBox.updateSdkList();
      }

      @Override
      public void jdkRemoved(Sdk jdk) {
        mySdkComboBox.updateSdkList();
      }

      @Override
      public void jdkNameChanged(Sdk jdk, String previousName) {
        mySdkComboBox.updateSdkList();
      }
    });
  }

  @Nls
  public String getDisplayName() {
    return "Python SDK";
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    return mySdkComboBox.getSelectedSdk() != getFacetConfiguration().getSdk();
  }

  private PythonFacetConfiguration getFacetConfiguration() {
    return ((PythonFacetConfiguration) myEditorContext.getFacet().getConfiguration());
  }

  public void apply() {
    getFacetConfiguration().setSdk(mySdkComboBox.getSelectedSdk());
  }

  public void reset() {
    mySdkComboBox.updateSdkList(getFacetConfiguration().getSdk(), false);
  }

  public void disposeUIResources() {
    Disposer.dispose(myConnection);
  }
}
