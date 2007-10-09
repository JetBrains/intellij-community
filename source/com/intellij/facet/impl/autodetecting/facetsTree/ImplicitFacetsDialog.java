/*
 * Copyright (c) 2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.autodetecting.ImplicitFacetInfo;
import com.intellij.facet.impl.autodetecting.ImplicitFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author nik
 */
public class ImplicitFacetsDialog extends DialogWrapper {
  private ImplicitFacetsTreeComponent myFacetsTreeComponent;
  private JPanel myMainPanel;
  private JPanel myTreePanel;
  private Action myEditSettingsAction = new EditSettingsAction();
  private final Project myProject;

  public ImplicitFacetsDialog(final Project project, final ImplicitFacetManager implicitFacetsManager, final List<ImplicitFacetInfo> implicitFacets) {
    super(project, true);
    myProject = project;
    setTitle(ProjectBundle.message("dialog.title.facets.detected"));
    myFacetsTreeComponent = new ImplicitFacetsTreeComponent(implicitFacetsManager, implicitFacets);
    DetectedFacetsTree tree = myFacetsTreeComponent.getTree();
    TreeUtil.expandAll(tree);
    myTreePanel.add(tree, BorderLayout.CENTER);
    setOKButtonText(ProjectBundle.message("button.text.accept.detected.facets"));
    setCancelButtonText(ProjectBundle.message("button.text.postpone.detected.facets"));
    init();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), myEditSettingsAction, getCancelAction()};
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected void doOKAction() {
    myFacetsTreeComponent.createAndDeleteFacets();
    super.doOKAction();
  }

  private class EditSettingsAction extends AbstractAction {
    private EditSettingsAction() {
      super(ProjectBundle.message("button.text.edit.facet.settings"));
    }

    public void actionPerformed(final ActionEvent e) {
      Facet selectedFacet = myFacetsTreeComponent.getSelectedFacet();
      if (selectedFacet != null) {
        ModulesConfigurator.showFacetSettingsDialog(selectedFacet, null);
      }
      else {
        ModulesConfigurator.showDialog(myProject, null, null, false);
      }
    }
  }
}
