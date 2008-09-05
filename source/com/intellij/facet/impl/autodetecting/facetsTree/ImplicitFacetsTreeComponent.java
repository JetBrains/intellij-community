/*
 * Copyright (c) 2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.FacetType;
import com.intellij.facet.Facet;
import com.intellij.facet.impl.autodetecting.DetectedFacetManager;
import com.intellij.facet.impl.autodetecting.model.DetectedFacetInfo;
import com.intellij.facet.impl.autodetecting.model.FacetInfo2;
import com.intellij.facet.impl.autodetecting.model.FacetInfoBackedByFacet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ImplicitFacetsTreeComponent {
  private DetectedFacetsTree myTree;
  private final DetectedFacetManager myDetectedFacetManager;
  private Collection<DetectedFacetsTree.FacetTypeNode> myFacetTypeNodes;

  public ImplicitFacetsTreeComponent(DetectedFacetManager detectedFacetManager, Collection<DetectedFacetInfo<Module>> detectedFacets, HashMap<DetectedFacetInfo<Module>, List<VirtualFile>> files) {
    myDetectedFacetManager = detectedFacetManager;

    Map<FacetType, DetectedFacetsTree.FacetTypeNode> facetTypeNodes = new HashMap<FacetType, DetectedFacetsTree.FacetTypeNode>();

    Collection<FacetInfo2<Module>> sortedFacets = new LinkedHashSet<FacetInfo2<Module>>();
    for (FacetInfo2<Module> facet : detectedFacets) {
      addUnderlying(facet, sortedFacets);
    }

    Map<FacetInfo2<Module>, DetectedFacetsTree.FacetNode> facetNodes = new HashMap<FacetInfo2<Module>, DetectedFacetsTree.FacetNode>();
    for (FacetInfo2<Module> facetInfo : sortedFacets) {
      FacetType facetType = getRootFacetType(facetInfo);
      DetectedFacetsTree.FacetTypeNode facetTypeNode = facetTypeNodes.get(facetType);
      if (facetTypeNode == null) {
        facetTypeNode = new DetectedFacetsTree.FacetTypeNode(facetType);
        facetTypeNodes.put(facetType, facetTypeNode);
      }

      Module module = facetInfo.getModule();
      ModuleNodeImpl moduleNode = findOrCreateModuleNode(facetTypeNode, module);

      DetectedFacetsTree.FacetNode parentNode = null;
      FacetInfo2<Module> underlyingFacet = facetInfo.getUnderlyingFacetInfo();
      if (underlyingFacet != null) {
        parentNode = facetNodes.get(underlyingFacet);
      }

      VirtualFile projectRoot = facetInfo.getModule().getProject().getBaseDir();
      DetectedFacetsTree.FacetNode facetNode;
      if (facetInfo instanceof DetectedFacetInfo) {
        facetNode = new FacetNodeImpl((DetectedFacetInfo<Module>)facetInfo, projectRoot, parentNode, files.get(facetInfo));
      }
      else {
        facetNode = new RealFacetNode((FacetInfoBackedByFacet)facetInfo, projectRoot, parentNode);
      }
      facetNodes.put(facetInfo, facetNode);
      if (parentNode == null) {
        moduleNode.addRootFacet(facetNode);
      }
    }

    myFacetTypeNodes = facetTypeNodes.values();
    myTree = new DetectedFacetsTree(myFacetTypeNodes);
  }

  public void createFacets() {
    for (DetectedFacetsTree.FacetTypeNode facetTypeNode : myFacetTypeNodes) {
      for (DetectedFacetsTree.ModuleNode moduleNode : facetTypeNode.getModuleNodes()) {
        boolean accept = facetTypeNode.isChecked() && moduleNode.isChecked();
        processFacetNodes(moduleNode.getRootFacets(), accept, null);
      }
    }
  }

  private void processFacetNodes(final List<DetectedFacetsTree.FacetNode> facetNodes, final boolean accept, final Facet underlyingFacet) {
    for (DetectedFacetsTree.FacetNode facetNode : facetNodes) {
      Facet facet = null;
      if (facetNode instanceof FacetNodeImpl) {
        DetectedFacetInfo<Module> detectedFacetInfo = ((FacetNodeImpl)facetNode).getDetectedFacetInfo();
        if (accept && facetNode.isChecked()) {
          facet = myDetectedFacetManager.createFacet(detectedFacetInfo, underlyingFacet);
        }
        else {
          myDetectedFacetManager.disableDetectionInFile(detectedFacetInfo);
        }
      }
      else {
        facet = ((RealFacetNode)facetNode).myFacetInfo.getFacet();
      }
      processFacetNodes(facetNode.getChildren(), accept && facetNode.isChecked(), facet);
    }
  }

  private static void addUnderlying(final FacetInfo2<Module> facet, final Collection<FacetInfo2<Module>> sortedFacets) {
    FacetInfo2<Module> underlying = facet.getUnderlyingFacetInfo();
    if (underlying != null && !sortedFacets.contains(underlying)) {
      addUnderlying(underlying, sortedFacets);
    }
    sortedFacets.add(facet);
  }

  private static ModuleNodeImpl findOrCreateModuleNode(final DetectedFacetsTree.FacetTypeNode facetTypeNode, final Module module) {
    for (DetectedFacetsTree.ModuleNode node : facetTypeNode.getModuleNodes()) {
      ModuleNodeImpl moduleNode = (ModuleNodeImpl)node;
      if (moduleNode.myModule.equals(module)) {
        return moduleNode;
      }
    }
    ModuleNodeImpl moduleNode = new ModuleNodeImpl(module);
    facetTypeNode.addModuleNode(moduleNode);
    return moduleNode;
  }

  private static FacetType getRootFacetType(final FacetInfo2<?> detectedFacet) {
    FacetInfo2<?> facet = detectedFacet;
    FacetInfo2<?> underlyingFacet;
    while ((underlyingFacet = facet.getUnderlyingFacetInfo()) != null) {
      facet = underlyingFacet;
    }
    return facet.getFacetType();
  }

  public DetectedFacetsTree getTree() {
    return myTree;
  }

  private static class FacetNodeImpl extends DetectedFacetsTree.FacetNode {
    private final DetectedFacetInfo<Module> myDetectedFacetInfo;

    private FacetNodeImpl(DetectedFacetInfo<Module> detectedFacet, VirtualFile projectRoot, @Nullable final DetectedFacetsTree.FacetNode parent,
                          final List<VirtualFile> files) {
      super(detectedFacet, detectedFacet.getFacetType(), projectRoot, files.toArray(new VirtualFile[files.size()]), parent);
      myDetectedFacetInfo = detectedFacet;
    }

    public DetectedFacetInfo<Module> getDetectedFacetInfo() {
      return myDetectedFacetInfo;
    }

    public String getName() {
      return myDetectedFacetInfo.getFacetName();
    }
  }

  private static class RealFacetNode extends DetectedFacetsTree.FacetNode {
    private final FacetInfoBackedByFacet myFacetInfo;

    private RealFacetNode(final FacetInfoBackedByFacet facetInfo, final VirtualFile projectRoot, @Nullable final DetectedFacetsTree.FacetNode parent) {
      super(facetInfo, facetInfo.getFacetType(), projectRoot, VirtualFile.EMPTY_ARRAY, parent);
      myFacetInfo = facetInfo;
    }

    public String getDescription() {
      return "";
    }

    public String getName() {
      return myFacetInfo.getFacetName();
    }
  }

  private static class ModuleNodeImpl extends DetectedFacetsTree.ModuleNode {
    private final Module myModule;

    private ModuleNodeImpl(final Module module) {
      super(module);
      myModule = module;
    }

    public String getModuleName() {
      return myModule.getName();
    }

    public String getModuleDescription() {
      return "";
    }
  }
}
