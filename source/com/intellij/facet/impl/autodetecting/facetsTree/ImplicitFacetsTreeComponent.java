/*
 * Copyright (c) 2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.impl.autodetecting.ImplicitFacetInfo;
import com.intellij.facet.impl.autodetecting.ImplicitFacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.ui.CheckedTreeNode;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ImplicitFacetsTreeComponent {
  private DetectedFacetsTree myTree;
  private final ImplicitFacetManager myImplicitFacetManager;
  private Collection<DetectedFacetsTree.FacetTypeNode> myFacetTypeNodes;

  public ImplicitFacetsTreeComponent(ImplicitFacetManager implicitFacetManager, List<ImplicitFacetInfo> implicitFacets) {
    myImplicitFacetManager = implicitFacetManager;
    Map<Facet, ImplicitFacetInfo> facet2info = new HashMap<Facet, ImplicitFacetInfo>();
    for (ImplicitFacetInfo implicitFacet : implicitFacets) {
      facet2info.put(implicitFacet.getFacet(), implicitFacet);
    }

    Map<FacetType, DetectedFacetsTree.FacetTypeNode> facetTypeNodes = new HashMap<FacetType, DetectedFacetsTree.FacetTypeNode>();
    Map<Module, ModuleNodeImpl> moduleNodes = new HashMap<Module, ModuleNodeImpl>();

    Collection<ImplicitFacetInfo> sortedFacets = new LinkedHashSet<ImplicitFacetInfo>();
    for (ImplicitFacetInfo facet : implicitFacets) {
      addUnderlying(facet, sortedFacets, facet2info);
    }

    Map<Facet, DetectedFacetsTree.FacetNode> facetNodes = new HashMap<Facet, DetectedFacetsTree.FacetNode>();
    for (ImplicitFacetInfo implicitFacet : sortedFacets) {
      FacetType facetType = getRootFacetType(implicitFacet, facet2info);
      DetectedFacetsTree.FacetTypeNode facetTypeNode = facetTypeNodes.get(facetType);
      if (facetTypeNode == null) {
        facetTypeNode = new DetectedFacetsTree.FacetTypeNode(facetType);
        facetTypeNodes.put(facetType, facetTypeNode);
      }

      Facet facet = implicitFacet.getFacet();
      Module module = facet.getModule();
      ModuleNodeImpl moduleNode = findOrCreateModuleNode(facetTypeNode, module);

      DetectedFacetsTree.FacetNode parentNode = null;
      Facet underlyingFacet = facet.getUnderlyingFacet();
      if (underlyingFacet != null) {
        parentNode = facetNodes.get(underlyingFacet);
      }

      DetectedFacetsTree.FacetNode facetNode = new FacetNodeImpl(implicitFacet, implicitFacet.getRelativeFileUrl(), parentNode);
      facetNodes.put(facet, facetNode);
      if (parentNode == null) {
        moduleNode.addRootFacet(facetNode);
      }
    }

    myFacetTypeNodes = facetTypeNodes.values();
    myTree = new DetectedFacetsTree(myFacetTypeNodes) {
      protected void onDoubleClick(final CheckedTreeNode node) {
        if (node instanceof FacetNodeImpl) {
          ModulesConfigurator.showFacetSettingsDialog(((FacetNodeImpl)node).getImplicitFacetInfo().getFacet(), null);
        }
      }
    };
  }

  public void createAndDeleteFacets() {
    for (DetectedFacetsTree.FacetTypeNode facetTypeNode : myFacetTypeNodes) {
      for (DetectedFacetsTree.ModuleNode moduleNode : facetTypeNode.getModuleNodes()) {
        boolean accept = facetTypeNode.isChecked() && moduleNode.isChecked();
        if (accept) {
          processFacetNodes(moduleNode.getRootFacets(), true);
        }
        else {
          myImplicitFacetManager.disableDetectionInModule(facetTypeNode.getFacetType(), ((ModuleNodeImpl)moduleNode).myModule);
        }
      }
    }
    myImplicitFacetManager.onImplicitFacetChanged();
  }

  private void processFacetNodes(final List<DetectedFacetsTree.FacetNode> facetNodes, final boolean accept) {
    for (DetectedFacetsTree.FacetNode facetNode : facetNodes) {
      ImplicitFacetInfo implicitFacetInfo = ((FacetNodeImpl)facetNode).getImplicitFacetInfo();
      if (accept && facetNode.isChecked()) {
        implicitFacetInfo.getFacet().setImplicit(false);
      }
      else {
        myImplicitFacetManager.disableDetectionInFile(implicitFacetInfo);
      }
      processFacetNodes(facetNode.getChildren(), accept && facetNode.isChecked());
    }
  }

  private static void addUnderlying(final ImplicitFacetInfo facet, final Collection<ImplicitFacetInfo> sortedFacets,
                             final Map<Facet, ImplicitFacetInfo> facet2info) {
    Facet underlying = facet.getFacet().getUnderlyingFacet();
    if (underlying != null) {
      ImplicitFacetInfo info = facet2info.get(underlying);
      if (info != null && !sortedFacets.contains(info)) {
        addUnderlying(info, sortedFacets, facet2info);
      }
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

  private static FacetType getRootFacetType(final ImplicitFacetInfo implicitFacet, final Map<Facet, ImplicitFacetInfo> facet2info) {
    Facet facet = implicitFacet.getFacet();
    Facet underlyingFacet;
    while ((underlyingFacet = facet.getUnderlyingFacet()) != null && facet2info.containsKey(underlyingFacet)) {
      facet = underlyingFacet;
    }
    return facet.getType();
  }

  public DetectedFacetsTree getTree() {
    return myTree;
  }

  private static class FacetNodeImpl extends DetectedFacetsTree.FacetNode {
    private final ImplicitFacetInfo myImplicitFacetInfo;

    private FacetNodeImpl(ImplicitFacetInfo implicitFacet, final String relativeFilePath, @Nullable final DetectedFacetsTree.FacetNode parent) {
      super(implicitFacet.getFacet(), implicitFacet.getFacet().getType(), relativeFilePath, implicitFacet.getFile(), parent);
      myImplicitFacetInfo = implicitFacet;
    }

    public ImplicitFacetInfo getImplicitFacetInfo() {
      return myImplicitFacetInfo;
    }

    public String getName() {
      return myImplicitFacetInfo.getFacet().getName();
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
