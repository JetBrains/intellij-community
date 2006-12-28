/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.impl.ui.FacetEditor;
import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.facet.impl.ui.ProjectConfigurableContext;
import com.intellij.facet.impl.ui.ConfigureFacetsStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ProjectFacetsConfigurator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetsConfigurator");
  private Map<Module, ModifiableFacetModel> myModels = new HashMap<Module, ModifiableFacetModel>();
  private Map<Facet, FacetEditor> myEditors = new HashMap<Facet, FacetEditor>();
  private Map<Module, FacetTreeModel> myTreeModels = new HashMap<Module, FacetTreeModel>();
  private Map<FacetInfo, Facet> myInfo2Facet = new HashMap<FacetInfo, Facet>();
  private Map<Facet, FacetInfo> myFacet2Info = new HashMap<Facet, FacetInfo>();


  public ProjectFacetsConfigurator() {
  }

  public void removeFacet(Facet facet) {
    getTreeModel(facet.getModule()).removeFacetInfo(myFacet2Info.get(facet));
    getOrCreateModifiableModel(facet.getModule()).removeFacet(facet);
  }

  public Facet createAndAddFacet(Module module, FacetType<?, ?> type, final @Nullable FacetInfo underlyingFacet) {
    final Facet facet = createFacet(type, module, myInfo2Facet.get(underlyingFacet));
    getOrCreateModifiableModel(module).addFacet(facet);
    addFacetInfo(facet);
    return facet;
  }

  private void addFacetInfo(final Facet facet) {
    LOG.assertTrue(!myFacet2Info.containsKey(facet));
    FacetInfo info = new FacetInfo(facet.getType(), facet.getConfiguration(), myFacet2Info.get(facet.getUnderlyingFacet()));
    myFacet2Info.put(facet, info);
    myInfo2Facet.put(info, facet);
    getTreeModel(facet.getModule()).addFacetInfo(info);
  }

  public void addFacetInfos(final Module module) {
    final Facet[] facets = getFacetModel(module).getSortedFacets();
    for (Facet facet : facets) {
      //todo[nik] remove later
      if (FacetTypeRegistry.getInstance().findFacetType(facet.getTypeId()) != null) {
        addFacetInfo(facet);
      }
    }
  }

  private static <C extends FacetConfiguration> Facet createFacet(final FacetType<?, C> type, final Module module, final @Nullable Facet underlyingFacet) {
    return type.createFacet(module, type.createDefaultConfiguration(), underlyingFacet);
  }

  private boolean isNewFacet(Facet facet) {
    final ModifiableFacetModel model = myModels.get(facet.getModule());
    return model != null && model.isNewFacet(facet);
  }

  @NotNull
  public ModifiableFacetModel getOrCreateModifiableModel(Module module) {
    ModifiableFacetModel model = myModels.get(module);
    if (model == null) {
      model = FacetManager.getInstance(module).createModifiableModel();
      myModels.put(module, model);
    }
    return model;
  }

  @NotNull
  public FacetEditor getOrCreateEditor(Facet facet) {
    FacetEditor editor = myEditors.get(facet);
    if (editor == null) {
      editor = new FacetEditor(new ProjectConfigurableContext(facet.getModule(), isNewFacet(facet)), facet.getConfiguration());
      editor.createComponent();
      editor.reset();
      myEditors.put(facet, editor);
    }
    return editor;
  }

  @NotNull
  public FacetModel getFacetModel(Module module) {
    final ModifiableFacetModel model = myModels.get(module);
    if (model != null) {
      return model;
    }
    return FacetManager.getInstance(module);
  }

  public void commitFacets() {
    for (ModifiableFacetModel model : myModels.values()) {
      model.commit();
    }

    for (Map.Entry<Facet, FacetEditor> entry : myEditors.entrySet()) {
      entry.getValue().onFacetAdded(entry.getKey().getModule());
    }

    myModels.clear();
  }

  public void reset() {
    for (FacetEditor editor : myEditors.values()) {
      editor.reset();
    }
  }

  public void applyAndDispose() throws ConfigurationException {
    for (FacetEditor editor : myEditors.values()) {
      editor.apply();
    }
    for (FacetEditor editor : myEditors.values()) {
      editor.disposeUIResources();
    }
  }

  public boolean isModified() {
    for (ModifiableFacetModel model : myModels.values()) {
      if (model.isModified()) {
        return true;
      }
    }
    for (FacetEditor editor : myEditors.values()) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  public FacetTreeModel getTreeModel(Module module) {
    FacetTreeModel treeModel = myTreeModels.get(module);
    if (treeModel == null) {
      treeModel = new FacetTreeModel();
      myTreeModels.put(module, treeModel);
    }
    return treeModel;
  }

  public FacetInfo getFacetInfo(final Facet facet) {
    return myFacet2Info.get(facet);
  }

  public Facet getFacet(final FacetInfo facetInfo) {
    return myInfo2Facet.get(facetInfo);
  }

  public void registerEditors(final Module module, ConfigureFacetsStep facetsStep) {
    final Map<FacetInfo, FacetEditor> info2EditorMap = facetsStep.getInfo2EditorMap();
    final Facet[] allFacets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : allFacets) {
      for (Map.Entry<FacetInfo, FacetEditor> entry : info2EditorMap.entrySet()) {
        if (entry.getKey().getConfiguration() == facet.getConfiguration()) {
          myEditors.put(facet, entry.getValue());
        }
      }
    }
  }
}
