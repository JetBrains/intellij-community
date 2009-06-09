package com.jetbrains.python.facet;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author yole
 */
public class PythonSdkEditorTab extends FacetEditorTab {
  private JPanel myMainPanel;
  private PythonSdkComboBox mySdkComboBox;
  private final FacetEditorContext myEditorContext;

  public PythonSdkEditorTab(final FacetEditorContext editorContext) {
    myEditorContext = editorContext;
    mySdkComboBox.setProject(editorContext.getProject());
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

  public void apply() throws ConfigurationException {
    getFacetConfiguration().setSdk(mySdkComboBox.getSelectedSdk());
  }

  public void reset() {
    mySdkComboBox.updateSdkList(getFacetConfiguration().getSdk(), false);
  }

  public void disposeUIResources() {
  }
}
