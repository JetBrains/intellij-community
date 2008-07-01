/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.*;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.facet.impl.ui.FacetDetectionProcessor;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class DetectedFacetsTreeComponent {
  private JPanel myMainPanel;
  private List<ModuleDescriptorNode> myModuleNodes = new ArrayList<ModuleDescriptorNode>();

  public DetectedFacetsTreeComponent() {
    myMainPanel = new JPanel(new BorderLayout());
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void addFacets(ModuleDescriptor moduleDescriptor, Map<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>> root2Facets) {
    for (File root : root2Facets.keySet()) {
      ModuleDescriptorNode moduleNode = new ModuleDescriptorNode(moduleDescriptor, root);

      Map<FacetInfo, DetectedFacetsTree.FacetNode> facetInfos = new HashMap<FacetInfo, DetectedFacetsTree.FacetNode>();
      for (FacetDetectionProcessor.DetectedInWizardFacetInfo detectedFacetInfo : root2Facets.get(root)) {
        DetectedFacetsTree.FacetNode parent = null;
        FacetInfo underlyingFacet = detectedFacetInfo.getFacetInfo().getUnderlyingFacet();
        if (underlyingFacet != null) {
          parent = facetInfos.get(underlyingFacet);
        }

        VirtualFile virtualRoot = LocalFileSystem.getInstance().findFileByIoFile(root);
        DetectedFacetsTree.FacetNode detectedFacet = new FacetInfoNode(detectedFacetInfo, virtualRoot, parent);
        facetInfos.put(detectedFacetInfo.getFacetInfo(), detectedFacet);
        if (parent == null) {
          moduleNode.addRootFacet(detectedFacet);
        }
      }

      myModuleNodes.add(moduleNode);
    }
  }

  public void createTree() {
    final CheckboxTreeBase tree = new DetectedFacetsTree(myModuleNodes);
    TreeUtil.expandAll(tree);
    myMainPanel.add(tree, BorderLayout.CENTER);
  }

  public void clear() {
    myMainPanel.removeAll();
    myModuleNodes.clear();
  }

  public void createFacets(final ModuleDescriptor descriptor, final Module module, final ModifiableRootModel rootModel) {
    List<Pair<FacetDetector, Facet>> createdFacets = new ArrayList<Pair<FacetDetector, Facet>>();
    ModifiableFacetModel modifiableModel = FacetManager.getInstance(module).createModifiableModel();
    for (ModuleDescriptorNode moduleNode : myModuleNodes) {
      if (moduleNode.myModuleDescriptor.equals(descriptor)) {
        processFacetsInfos(moduleNode.getRootFacets(), module, rootModel, modifiableModel, null, createdFacets, moduleNode.isChecked());
      }
    }
    modifiableModel.commit();
    for (Pair<FacetDetector, Facet> createdFacet : createdFacets) {
      createdFacet.getFirst().afterFacetAdded(createdFacet.getSecond());
    }
  }

  private static void processFacetsInfos(final List<DetectedFacetsTree.FacetNode> facets, final Module module, final ModifiableRootModel rootModel,
                                         final ModifiableFacetModel facetModel, Facet underlyingFacet,
                                         final List<Pair<FacetDetector, Facet>> createdFacets, boolean createFacets) {
    for (DetectedFacetsTree.FacetNode facetNode : facets) {
      boolean createFacet = createFacets && facetNode.isChecked();
      FacetDetectionProcessor.DetectedInWizardFacetInfo detectedFacetInfo = ((FacetInfoNode)facetNode).getDetectedFacetInfo();
      FacetInfo facetInfo = detectedFacetInfo.getFacetInfo();
      FacetType type = facetInfo.getFacetType();
      Facet facet = null;

      if (createFacet) {
        //noinspection unchecked
        facet = FacetManager.getInstance(module).createFacet(type, facetInfo.getName(), facetInfo.getConfiguration(), underlyingFacet);
        FacetDetector facetDetector = detectedFacetInfo.getFacetDetector();
        facetDetector.beforeFacetAdded(facet, facetModel, rootModel);
        
        facetModel.addFacet(facet);
        createdFacets.add(Pair.create(facetDetector, facet));
      }
      else {
        VirtualFile[] files = facetNode.getFiles();
        String[] urls = new String[files.length];
        for (int i = 0; i < files.length; i++) {
          urls[i] = files[i].getUrl();
        }
        FacetAutodetectingManager.getInstance(module.getProject()).disableAutodetectionInFiles(type, module, urls);
      }

      processFacetsInfos(facetNode.getChildren(), module, rootModel, facetModel, facet, createdFacets, createFacet);
    }
  }

  private static class FacetInfoNode extends DetectedFacetsTree.FacetNode {
    private FacetDetectionProcessor.DetectedInWizardFacetInfo myDetectedFacetInfo;

    private FacetInfoNode(final FacetDetectionProcessor.DetectedInWizardFacetInfo detectedFacetInfo, final VirtualFile root, @Nullable final DetectedFacetsTree.FacetNode parent) {
      super(detectedFacetInfo.getFacetInfo(), detectedFacetInfo.getFacetInfo().getFacetType(), root, new VirtualFile[]{detectedFacetInfo.getFile()}, parent);
      myDetectedFacetInfo = detectedFacetInfo;
    }

    public FacetDetectionProcessor.DetectedInWizardFacetInfo getDetectedFacetInfo() {
      return myDetectedFacetInfo;
    }
  }

  private static class ModuleDescriptorNode extends DetectedFacetsTree.ModuleNode {
    private final ModuleDescriptor myModuleDescriptor;
    private final File myRoot;

    private ModuleDescriptorNode(ModuleDescriptor moduleDescriptor, final File root) {
      super(moduleDescriptor);
      myModuleDescriptor = moduleDescriptor;
      myRoot = root;
    }

    public String getModuleName() {
      return myModuleDescriptor.getName();
    }

    public String getModuleDescription() {
      return " (" + myRoot.getPath() + ")";
    }
  }

}
