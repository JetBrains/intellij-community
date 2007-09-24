/*
 * Copyright (c) 2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.facet.impl.autodetecting.ImplicitFacetManager;
import com.intellij.facet.impl.autodetecting.ImplicitFacetInfo;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.awt.*;

/**
 * @author nik
 */
public class ImplicitFacetsDialog extends DialogWrapper {
  private ImplicitFacetsTreeComponent myFacetsTreeComponent;
  private JPanel myMainPanel;
  private JPanel myTreePanel;

  public ImplicitFacetsDialog(final Project project, final ImplicitFacetManager implicitFacetsManager, final List<ImplicitFacetInfo> implicitFacets) {
    super(project, true);
    setTitle(ProjectBundle.message("dialog.title.facets.detected"));
    myFacetsTreeComponent = new ImplicitFacetsTreeComponent(implicitFacetsManager, implicitFacets);
    DetectedFacetsTree tree = myFacetsTreeComponent.getTree();
    TreeUtil.expandAll(tree);
    tree.setBackground(myTreePanel.getBackground());
    myTreePanel.add(tree, BorderLayout.CENTER);
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected void doOKAction() {
    myFacetsTreeComponent.createAndDeleteFacets();
    super.doOKAction();
  }
}
