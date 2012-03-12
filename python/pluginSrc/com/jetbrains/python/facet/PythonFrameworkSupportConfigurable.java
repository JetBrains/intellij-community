package com.jetbrains.python.facet;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.LabeledComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class PythonFrameworkSupportConfigurable extends FrameworkSupportConfigurable {
  private final JComponent myMainPanel;
  private final PythonSdkComboBox mySdkComboBox;

  public PythonFrameworkSupportConfigurable(FrameworkSupportModel model) {
    mySdkComboBox = new PythonSdkComboBox();
    mySdkComboBox.setProject(model.getProject());
    myMainPanel = LabeledComponent.create(mySdkComboBox, "Python SDK:");
    ((LabeledComponent)myMainPanel).setLabelLocation(BorderLayout.WEST);
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  public void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, @Nullable Library library) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    PythonFacet facet = facetManager.createFacet(PythonFacetType.getInstance(), "Python", null);
    facet.getConfiguration().setSdk(mySdkComboBox.getSelectedSdk());
    facetModel.addFacet(facet);
    facetModel.commit();
  }
}
