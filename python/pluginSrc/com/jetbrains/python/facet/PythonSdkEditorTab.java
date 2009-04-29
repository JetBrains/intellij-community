package com.jetbrains.python.facet;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author yole
 */
public class PythonSdkEditorTab extends FacetEditorTab {
  private JPanel myMainPanel;
  private ComboboxWithBrowseButton mySdkComboBox;
  private final FacetEditorContext myEditorContext;

  public PythonSdkEditorTab(final FacetEditorContext editorContext) {
    myEditorContext = editorContext;
    mySdkComboBox.getComboBox().setRenderer(new SdkListCellRenderer());
    mySdkComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Sdk selectedSdk = getSelectedSdk();
        ProjectJdksEditor editor = new ProjectJdksEditor(selectedSdk, myEditorContext.getProject(), myMainPanel);
        editor.show();
        if (editor.isOK()) {
          selectedSdk = editor.getSelectedJdk();
          updateSdkList(selectedSdk);
        }
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
    return getSelectedSdk() != getFacetConfiguration().getSdk();
  }

  private PythonFacetConfiguration getFacetConfiguration() {
    return ((PythonFacetConfiguration) myEditorContext.getFacet().getConfiguration());
  }

  public void apply() throws ConfigurationException {
    getFacetConfiguration().setSdk(getSelectedSdk());
  }

  private Sdk getSelectedSdk() {
    return (Sdk) mySdkComboBox.getComboBox().getSelectedItem();
  }

  public void reset() {
    updateSdkList(getFacetConfiguration().getSdk());
  }

  private void updateSdkList(Sdk sdkToSelect) {
    final List<Sdk> sdkList = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    sdkList.add(0, null);
    mySdkComboBox.getComboBox().setModel(new DefaultComboBoxModel(sdkList.toArray(new Sdk[sdkList.size()])));
    mySdkComboBox.getComboBox().setSelectedItem(sdkToSelect);
  }

  public void disposeUIResources() {
  }
}
