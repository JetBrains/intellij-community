// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.LabeledComponent;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


public class PythonFrameworkSupportConfigurable extends FrameworkSupportConfigurable {
  private final JComponent myMainPanel;
  private final PythonSdkComboBox mySdkComboBox;

  public PythonFrameworkSupportConfigurable(FrameworkSupportModel model) {
    mySdkComboBox = new PythonSdkComboBox();
    mySdkComboBox.setProject(model.getProject());
    myMainPanel = LabeledComponent.create(mySdkComboBox, PyBundle.message("framework.support.python.sdk.combobox.label"));
    ((LabeledComponent<?>)myMainPanel).setLabelLocation(BorderLayout.WEST);
  }

  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, @Nullable Library library) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    JavaPythonFacet facet = facetManager.createFacet(JavaPythonFacetType.getInstance(), "Python", null);
    facet.getConfiguration().setSdk(mySdkComboBox.getSelectedSdk());
    facetModel.addFacet(facet);
    facetModel.commit();
  }
}
