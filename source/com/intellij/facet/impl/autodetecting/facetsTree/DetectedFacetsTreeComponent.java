/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.*;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  public void addFacets(ModuleDescriptor moduleDescriptor, Map<File, List<Pair<FacetInfo, VirtualFile>>> root2Facets) {
    for (File root : root2Facets.keySet()) {
      ModuleDescriptorNode moduleNode = new ModuleDescriptorNode(moduleDescriptor, root);

      Map<FacetInfo, DetectedFacetsTree.FacetNode> facetInfos = new HashMap<FacetInfo, DetectedFacetsTree.FacetNode>();
      for (Pair<FacetInfo, VirtualFile> pair : root2Facets.get(root)) {
        DetectedFacetsTree.FacetNode parent = null;
        FacetInfo underlyingFacet = pair.getFirst().getUnderlyingFacet();
        if (underlyingFacet != null) {
          parent = facetInfos.get(underlyingFacet);
        }

        VirtualFile virtualRoot = LocalFileSystem.getInstance().findFileByIoFile(root);
        DetectedFacetsTree.FacetNode detectedFacet = new FacetInfoNode(pair.getFirst(), virtualRoot, pair.getSecond(), parent);
        facetInfos.put(pair.getFirst(), detectedFacet);
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
    ModifiableFacetModel modifiableModel = FacetManager.getInstance(module).createModifiableModel();
    for (ModuleDescriptorNode moduleNode : myModuleNodes) {
      if (moduleNode.myModuleDescriptor.equals(descriptor)) {
        processFacetsInfos(moduleNode.getRootFacets(), module, rootModel, modifiableModel, null, moduleNode.isChecked());
      }
    }
    modifiableModel.commit();
  }

  private static void processFacetsInfos(final List<DetectedFacetsTree.FacetNode> facets, final Module module, final ModifiableRootModel rootModel,
                                   final ModifiableFacetModel facetModel, Facet underlyingFacet, boolean createFacets) {
    for (DetectedFacetsTree.FacetNode facetNode : facets) {
      boolean createFacet = createFacets && facetNode.isChecked();
      FacetInfo facetInfo = ((FacetInfoNode)facetNode).getFacetInfo();
      FacetType type = facetInfo.getFacetType();
      Facet facet = null;

      if (createFacet) {
        //noinspection unchecked
        facet = FacetManagerImpl.createFacet(type, module, facetInfo.getName(), facetInfo.getConfiguration(), underlyingFacet);
        facetModel.addFacet(facet);
      }
      else {
        VirtualFile[] files = facetNode.getFiles();
        String[] urls = new String[files.length];
        for (int i = 0; i < files.length; i++) {
          urls[i] = files[i].getUrl();
        }
        FacetAutodetectingManager.getInstance(module.getProject()).disableAutodetectionInFiles(type, module, urls);
      }

      processFacetsInfos(facetNode.getChildren(), module, rootModel, facetModel, facet, createFacet);
    }
  }

  private static class FacetInfoNode extends DetectedFacetsTree.FacetNode {
    private FacetInfo myFacetInfo;

    private FacetInfoNode(final FacetInfo facetInfo, final VirtualFile root, final VirtualFile file, @Nullable final DetectedFacetsTree.FacetNode parent) {
      super(facetInfo, facetInfo.getFacetType(), root, new VirtualFile[]{file}, parent);
      myFacetInfo = facetInfo;
    }

    public FacetInfo getFacetInfo() {
      return myFacetInfo;
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
