package com.jetbrains.python.facet;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportConfigurable;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class PythonFrameworkSupportConfigurable extends FrameworkSupportConfigurable {
  private PythonSdkComboBox mySdkComboBox;

  public PythonFrameworkSupportConfigurable(FrameworkSupportModel model) {
    mySdkComboBox = new PythonSdkComboBox();
    mySdkComboBox.setProject(model.getProject());
  }

  public JComponent getComponent() {
    return mySdkComboBox;
  }

  public void addSupport(Module module, ModifiableRootModel model, @Nullable Library library) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    PythonFacet facet = facetManager.createFacet(PythonFacetType.getInstance(), "Python", null);
    facet.getConfiguration().setSdk(mySdkComboBox.getSelectedSdk());
    facetModel.addFacet(facet);
    facetModel.commit();
  }
}
