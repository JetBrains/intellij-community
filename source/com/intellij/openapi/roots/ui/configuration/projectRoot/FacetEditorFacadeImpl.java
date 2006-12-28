/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.impl.ui.FacetEditorFacade;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.*;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MasterDetailsComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.HashMap;

/**
 * @author nik
 */
public class FacetEditorFacadeImpl implements FacetEditorFacade {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.projectRoot.FacetEditorFacadeImpl");
  private ProjectRootConfigurable myConfigurable;
  private Map<Facet, MasterDetailsComponent.MyNode> myNodes = new HashMap<Facet, MasterDetailsComponent.MyNode>();

  public FacetEditorFacadeImpl(final ProjectRootConfigurable configurable) {
    myConfigurable = configurable;
  }

  public void addFacetsNodes(final Module module, final MasterDetailsComponent.MyNode moduleNode) {
    getFacetConfigurator().addFacetInfos(module);

    final FacetModel facetModel = getFacetConfigurator().getFacetModel(module);
    for (Facet facet : facetModel.getSortedFacets()) {
      //todo[nik] remove later
      if (FacetTypeRegistry.getInstance().findFacetType(facet.getTypeId()) != null) {
        addFacetNode(facet, moduleNode);
      }
    }
  }

  private MasterDetailsComponent.MyNode addFacetNode(final Facet facet, final MasterDetailsComponent.MyNode moduleNode) {
    final MasterDetailsComponent.MyNode facetNode = new MasterDetailsComponent.MyNode(new FacetConfigurable(facet, getFacetConfigurator()));
    myNodes.put(facet, facetNode);
    MasterDetailsComponent.MyNode parent = moduleNode;
    final Facet underlyingFacet = facet.getUnderlyingFacet();
    if (underlyingFacet != null) {
      parent = myNodes.get(underlyingFacet);
      LOG.assertTrue(parent != null);
    }
    myConfigurable.addNode(facetNode, parent);
    return facetNode;
  }

  public boolean nodeHasFacetOfType(final FacetInfo facet, FacetTypeId typeId) {
    final Module selectedModule = getSelectedModule();
    if (selectedModule == null) {
      return false;
    }
    return getFacetConfigurator().getTreeModel(selectedModule).hasFacetOfType(facet, typeId);
  }

  public void createFacet(final FacetInfo parent, FacetType type) {
    Module module = getSelectedModule();

    final Facet facet = getFacetConfigurator().createAndAddFacet(module, type, parent);
    final MasterDetailsComponent.MyNode node = addFacetNode(facet, myConfigurable.findModuleNode(module));
    myConfigurable.selectNodeInTree(node);
  }

  @Nullable
  public FacetInfo getParent(final FacetInfo facetInfo) {
    final Module module = getFacetConfigurator().getFacet(facetInfo).getModule();
    return getFacetConfigurator().getTreeModel(module).getParent(facetInfo);
  }

  private ProjectFacetsConfigurator getFacetConfigurator() {
    return myConfigurable.getFacetConfigurator();
  }

  @Nullable
  private Facet getSelectedFacet() {
    final Object selectedObject = myConfigurable.getSelectedObject();
    if (selectedObject instanceof Facet) {
      return (Facet)selectedObject;
    }
    return null;
  }

  public void deleteSelectedFacet() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public Module getSelectedModule() {
    final Object selected = myConfigurable.getSelectedObject();
    if (selected instanceof Module) {
      return (Module)selected;
    }
    if (selected instanceof Facet) {
      return ((Facet)selected).getModule();
    }
    return null;
  }

  @Nullable
  public ModuleType getSelectedModuleType() {
    final Module module = getSelectedModule();
    return module != null ? module.getModuleType() : null;
  }

  @Nullable
  public FacetInfo getSelectedFacetInfo() {
    final Facet facet = getSelectedFacet();
    return facet != null ? getFacetConfigurator().getFacetInfo(facet) : null;
  }
}
