/*
 * Copyright (c) 2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.impl.autodetecting.DetectedFacetManager;
import com.intellij.facet.impl.autodetecting.model.DetectedFacetInfo;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * @author nik
 */
public class DetectedFacetsDialog extends DialogWrapper {
  private ImplicitFacetsTreeComponent myFacetsTreeComponent;
  private JPanel myMainPanel;
  private JPanel myTreePanel;

  public DetectedFacetsDialog(final Project project, final DetectedFacetManager detectedFacetsManager, final Collection<DetectedFacetInfo<Module>> detectedFacets,
                              final HashMap<DetectedFacetInfo<Module>, List<VirtualFile>> files) {
    super(project, true);
    setTitle(ProjectBundle.message("dialog.title.facets.detected"));
    myFacetsTreeComponent = new ImplicitFacetsTreeComponent(detectedFacetsManager, detectedFacets, files);
    DetectedFacetsTree tree = myFacetsTreeComponent.getTree();
    TreeUtil.expandAll(tree);
    myTreePanel.add(tree, BorderLayout.CENTER);
    setOKButtonText(ProjectBundle.message("button.text.accept.detected.facets"));
    setCancelButtonText(ProjectBundle.message("button.text.postpone.detected.facets"));
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected void doOKAction() {
    new WriteAction() {
      protected void run(final Result result) {
        myFacetsTreeComponent.createFacets();
      }
    }.execute();
    super.doOKAction();
  }

}
