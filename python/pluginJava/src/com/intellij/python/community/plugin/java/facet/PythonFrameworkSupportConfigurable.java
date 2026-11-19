// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.python.community.plugin.impl.facet.PythonFacet;
import com.intellij.python.community.plugin.impl.facet.PythonFacetType;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.python.sdk.backend.PyInterpreterLibraryFlowKt;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.configuration.PyModuleInterpreterSetter;
import com.jetbrains.python.configuration.interpreter.PyInterpreterComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Adds the Python facet to a module. In the interpreter library flow, the user picks an interpreter of a Python project,
 * and the module gets it after the commit of its root model. In the legacy flow, the user picks a Python SDK.
 */
final class PythonFrameworkSupportConfigurable extends FrameworkSupportConfigurable {
  private final JComponent myMainPanel;
  private final @Nullable PythonSdkComboBox mySdkComboBox;
  private final @Nullable PyInterpreterComboBox myInterpreterComboBox;

  PythonFrameworkSupportConfigurable(FrameworkSupportModel model) {
    Project project = model.getProject();
    if (PyInterpreterLibraryFlowKt.isInterpreterLibraryFlowEnabled()) {
      mySdkComboBox = null;
      // The new module does not exist yet, so the combo lists the interpreters of all Python projects.
      myInterpreterComboBox = project == null ? null : new PyInterpreterComboBox(project, () -> null, null);
      myMainPanel = myInterpreterComboBox == null
                    ? new JPanel()
                    : LabeledComponent.create(myInterpreterComboBox.getComponent(),
                                              PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.label"),
                                              BorderLayout.WEST);
    }
    else {
      myInterpreterComboBox = null;
      mySdkComboBox = new PythonSdkComboBox();
      mySdkComboBox.setProject(project);
      myMainPanel = LabeledComponent.create(mySdkComboBox, PyBundle.message("framework.support.python.sdk.combobox.label"), BorderLayout.WEST);
    }
  }

  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, @Nullable Library library) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    PythonFacet facet = facetManager.createFacet(PythonFacetType.getInstance(), "Python", null);
    if (mySdkComboBox != null) {
      facet.getConfiguration().setSdk(mySdkComboBox.getSelectedSdk());
    }
    facetModel.addFacet(facet);
    facetModel.commit();
    PythonInterpreter interpreter = myInterpreterComboBox == null ? null : myInterpreterComboBox.getSelectedInterpreter();
    if (interpreter != null) {
      // The root model commits after this call, in a write action, and a modal dialog can hold more models of the module.
      // So the interpreter is written when no modal dialog is open.
      ApplicationManager.getApplication().invokeLater(() -> PyModuleInterpreterSetter.setPythonInterpreterBlocking(module, interpreter),
                                                      ModalityState.nonModal(), module.getDisposed());
    }
  }
}
