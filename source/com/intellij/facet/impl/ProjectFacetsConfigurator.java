/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.facet.impl.ui.ConfigureFacetsStep;
import com.intellij.facet.impl.ui.FacetEditor;
import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.facet.impl.ui.ProjectConfigurableContext;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ProjectFacetsConfigurator implements FacetsProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetsConfigurator");
  private Map<Module, ModifiableFacetModel> myModels = new HashMap<Module, ModifiableFacetModel>();
  private Map<Facet, FacetEditor> myEditors = new HashMap<Facet, FacetEditor>();
  private Map<Module, FacetTreeModel> myTreeModels = new HashMap<Module, FacetTreeModel>();
  private Map<FacetInfo, Facet> myInfo2Facet = new HashMap<FacetInfo, Facet>();
  private Map<Facet, FacetInfo> myFacet2Info = new HashMap<Facet, FacetInfo>();
  private Map<Module, UserDataHolder> mySharedModuleData = new HashMap<Module, UserDataHolder>();
  private Set<Facet> myChangedFacets = new HashSet<Facet>();
  private final ProjectRootConfigurable myProjectRootConfigurable;
  private final NotNullFunction<Module, ModuleConfigurationState> myModuleStateProvider;

  public ProjectFacetsConfigurator(final ProjectRootConfigurable projectRootConfigurable, NotNullFunction<Module, ModuleConfigurationState> moduleStateProvider) {
    myProjectRootConfigurable = projectRootConfigurable;
    myModuleStateProvider = moduleStateProvider;
  }

  public void removeFacet(Facet facet) {
    getTreeModel(facet.getModule()).removeFacetInfo(myFacet2Info.get(facet));
    getOrCreateModifiableModel(facet.getModule()).removeFacet(facet);
    myChangedFacets.remove(facet);
    final FacetEditor facetEditor = myEditors.remove(facet);
    if (facetEditor != null) {
      facetEditor.disposeUIResources();
    }
    final FacetInfo facetInfo = myFacet2Info.remove(facet);
    if (facetInfo != null) {
      myInfo2Facet.remove(facetInfo);
    }
  }

  public Facet createAndAddFacet(Module module, FacetType<?, ?> type, String name, final @Nullable FacetInfo underlyingFacet) {
    final Facet facet = createFacet(type, module, name, myInfo2Facet.get(underlyingFacet));
    getOrCreateModifiableModel(module).addFacet(facet);
    addFacetInfo(facet);
    return facet;
  }

  private void addFacetInfo(final Facet facet) {
    LOG.assertTrue(!myFacet2Info.containsKey(facet));
    FacetInfo info = new FacetInfo(facet.getType(), facet.getName(), facet.getConfiguration(), myFacet2Info.get(facet.getUnderlyingFacet()));
    myFacet2Info.put(facet, info);
    myInfo2Facet.put(info, facet);
    getTreeModel(facet.getModule()).addFacetInfo(info);
  }

  public void addFacetInfos(final Module module) {
    final Facet[] facets = getFacetModel(module).getSortedFacets();
    for (Facet facet : facets) {
      addFacetInfo(facet);
    }
  }

  private static <C extends FacetConfiguration> Facet createFacet(final FacetType<?, C> type, final Module module, String name, final @Nullable Facet underlyingFacet) {
    return FacetManagerImpl.createFacet(type, module, name, type.createDefaultConfiguration(), underlyingFacet);
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
      final Facet underlyingFacet = facet.getUnderlyingFacet();
      final FacetEditorContext parentContext = underlyingFacet != null ? getOrCreateEditor(underlyingFacet).getContext() : null;
      final ModuleConfigurationState state = myModuleStateProvider.fun(facet.getModule());
      final ProjectConfigurableContext context = new MyProjectConfigurableContext(facet, parentContext, state);
      editor = new FacetEditor(context, facet.getConfiguration());
      editor.getComponent();
      editor.reset();
      myEditors.put(facet, editor);
    }
    return editor;
  }

  private UserDataHolder getSharedModuleData(final Module module) {
    UserDataHolder dataHolder = mySharedModuleData.get(module);
    if (dataHolder == null) {
      dataHolder = new UserDataHolderBase();
      mySharedModuleData.put(module, dataHolder);
    }
    return dataHolder;
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
      entry.getValue().onFacetAdded(entry.getKey());
    }

    myModels.clear();
    for (Facet facet : myChangedFacets) {
      facet.getModule().getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
    }
    myChangedFacets.clear();
  }

  public void resetEditors() {
    for (FacetEditor editor : myEditors.values()) {
      editor.reset();
    }
  }

  public void applyEditors() throws ConfigurationException {
    for (Map.Entry<Facet,FacetEditor> entry : myEditors.entrySet()) {
      final FacetEditor editor = entry.getValue();
      if (editor.isModified()) {
        myChangedFacets.add(entry.getKey());
      }
      editor.apply();
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

  public void disposeEditors() {
    for (FacetEditor editor : myEditors.values()) {
      editor.disposeUIResources();
    }
  }

  @NotNull
  public Facet[] getAllFacets(final Module module) {
    return getFacetModel(module).getAllFacets();
  }

  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(final Module module, final FacetTypeId<F> type) {
    return getFacetModel(module).getFacetsByType(type);
  }

  @Nullable
  public <F extends Facet> F findFacet(final Module module, final FacetTypeId<F> type, final String name) {
    return getFacetModel(module).findFacet(type, name);
  }

  private class MyProjectConfigurableContext extends ProjectConfigurableContext {
    public MyProjectConfigurableContext(final Facet facet, final FacetEditorContext parentContext, final ModuleConfigurationState state) {
      super(facet, ProjectFacetsConfigurator.this.isNewFacet(facet), parentContext, state,
            ProjectFacetsConfigurator.this.getSharedModuleData(facet.getModule()));
    }

    public Library createProjectLibrary(final String baseName, final VirtualFile[] roots) {
      LibraryTableModifiableModelProvider provider = myProjectRootConfigurable.createModifiableModelProvider(LibraryTablesRegistrar.PROJECT_LEVEL, false);
      LibraryTable.ModifiableModel model = provider.getModifiableModel();
      Library library = model.createLibrary(getUniqueLibraryName(baseName, model));
      LibraryEditor libraryEditor = ((LibrariesModifiableModel)model).getLibraryEditor(library);
      for (VirtualFile root : roots) {
        libraryEditor.addRoot(root, OrderRootType.CLASSES);
      }
      return library;
    }

  }
}
