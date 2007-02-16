/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.facet.impl.FacetModelBase;
import com.intellij.facet.impl.FacetModelImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.*;
import com.intellij.util.EventDispatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class FacetManagerImpl extends FacetManager implements ModuleComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.FacetManagerImpl");
  @NonNls private static final String FACET_ELEMENT = "facet";
  @NonNls private static final String TYPE_ATTRIBUTE = "type";
  @NonNls private static final String CONFIGURATION_ELEMENT = "configuration";
  @NonNls private static final String NAME_ATTRIBUTE = "name";

  private EventDispatcher<FacetManagerListener> myDispatcher = EventDispatcher.create(FacetManagerListener.class);
  private Module myModule;
  private FacetTypeRegistry myFacetTypeRegistry;
  private FacetManagerModel myModel = new FacetManagerModel();
  private boolean myHasNoFacetsFromBeginning = true;

  private boolean myInsideCommit = false;

  public FacetManagerImpl(final Module module, final FacetTypeRegistry facetTypeRegistry) {
    myModule = module;
    myFacetTypeRegistry = facetTypeRegistry;
  }

  public void addListener(FacetManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(FacetManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public ModifiableFacetModel createModifiableModel() {
    return new FacetModelImpl(this);
  }

  public void createAndCommitFacets(final FacetInfo[] facetInfos) {
    final ModifiableFacetModel model = createModifiableModel();
    Map<FacetInfo, Facet> info2Facet = new HashMap<FacetInfo, Facet>();
    for (FacetInfo info : facetInfos) {
      model.addFacet(getOrCreateFacet(info2Facet, info));
    }
    model.commit();
  }

  @Nullable
  public <F extends Facet> F findFacet(final FacetTypeId<F> type, final String name) {
    final Collection<F> fs = getFacetsByType(type);
    for (F f : fs) {
      if (f.getName().equals(name)) {
        return f;
      }
    }
    return null;
  }

  private Facet getOrCreateFacet(final Map<FacetInfo, Facet> info2Facet, final FacetInfo info) {
    Facet facet = info2Facet.get(info);
    if (facet == null) {
      final FacetInfo underlyingFacetInfo = info.getUnderlyingFacet();
      final Facet underlyingFacet = underlyingFacetInfo != null ? getOrCreateFacet(info2Facet, underlyingFacetInfo) : null;
      facet = info.getFacetType().createFacet(myModule, info.getName(), info.getConfiguration(), underlyingFacet);
      info2Facet.put(info, facet);
    }

    return facet;
  }

  @NotNull
  public Facet[] getAllFacets() {
    return myModel.getAllFacets();
  }

  @Nullable
  public <F extends Facet> F getFacetByType(FacetTypeId<F> typeId) {
    return myModel.getFacetByType(typeId);
  }


  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(FacetTypeId<F> typeId) {
    return myModel.getFacetsByType(typeId);
  }


  @NotNull
  public Facet[] getSortedFacets() {
    return myModel.getSortedFacets();
  }

  public void readExternal(Element element) throws InvalidDataException {
    List<Facet> facets = new ArrayList<Facet>();
    addFacets(facets, element, null);
    myHasNoFacetsFromBeginning &= facets.isEmpty();
    myModel.setAllFacets(facets.toArray(new Facet[facets.size()]));
  }

  private void addFacets(final List<Facet> facets, final Element element, final Facet underlyingFacet) throws InvalidDataException {
    List<Element> children = element.getChildren(FACET_ELEMENT);
    for (Element child : children) {
      final String value = child.getAttributeValue(TYPE_ATTRIBUTE);
      if (value != null) {
        final FacetType<?,?> type = myFacetTypeRegistry.findFacetType(value);
        if (type != null) {
          addFacet(type, child, facets, underlyingFacet);
        }
      }
    }
  }

  private <C extends FacetConfiguration> void addFacet(final FacetType<?, C> type, final Element element, final List<Facet> facets,
                                                       final Facet underlyingFacet) throws InvalidDataException {
    final C configuration = type.createDefaultConfiguration();
    final Element config = element.getChild(CONFIGURATION_ELEMENT);
    if (config != null) {
      configuration.readExternal(config);
    }
    String name = element.getAttributeValue(NAME_ATTRIBUTE);
    if (name == null) {
      //todo[nik] remove later. This code is written only for compatibility with first Selena EAPs
      name = type.getPresentableName();
    }
    final Facet facet = type.createFacet(myModule, name, configuration, underlyingFacet);
    facets.add(facet);
    addFacets(facets, element, facet);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Facet[] facets = getSortedFacets();
    if (facets.length == 0 && myHasNoFacetsFromBeginning) {
      throw new WriteExternalException();
    }

    Map<Facet, Element> elements = new HashMap<Facet, Element>();
    elements.put(null, element);

    for (Facet facet : facets) {
      final Facet underlyingFacet = facet.getUnderlyingFacet();
      final Element parent = elements.get(underlyingFacet);

      Element child = new Element(FACET_ELEMENT);
      child.setAttribute(TYPE_ATTRIBUTE, facet.getType().getStringId());
      child.setAttribute(NAME_ATTRIBUTE, facet.getName());
      final Element config = new Element(CONFIGURATION_ELEMENT);
      try {
        facet.getConfiguration().writeExternal(config);
      }
      catch (WriteExternalException e) {
        continue;
      }
      child.addContent(config);

      parent.addContent(child);
      elements.put(facet, child);
    }

    if (element.getContentSize() == 0 && myHasNoFacetsFromBeginning) {
      throw new WriteExternalException();
    }
  }

  public void commit(final ModifiableFacetModel model) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(!myInsideCommit, "Recursive commit");
    try {
      myInsideCommit = true;

      Set<Facet> toRemove = new HashSet<Facet>(Arrays.asList(getAllFacets()));
      List<Facet> toAdd = new ArrayList<Facet>();

      for (Facet facet : model.getAllFacets()) {
        boolean isNew = !toRemove.remove(facet);
        if (isNew) {
          toAdd.add(facet);
        }
      }

      List<Facet> newFacets = new ArrayList<Facet>();
      for (Facet facet : getAllFacets()) {
        if (!toRemove.contains(facet)) {
          newFacets.add(facet);
        }
      }
      newFacets.addAll(toAdd);

      List<FacetRenameInfo> toRename = new ArrayList<FacetRenameInfo>();
      for (Facet facet : newFacets) {
        final String newName = model.getNewName(facet);
        if (newName != null && !newName.equals(facet.getName())) {
          toRename.add(new FacetRenameInfo(facet, facet.getName(), newName));
        }
      }

      for (Facet facet : toAdd) {
        myDispatcher.getMulticaster().beforeFacetAdded(facet);
      }
      for (Facet facet : toRemove) {
        myDispatcher.getMulticaster().beforeFacetRemoved(facet);
      }
      for (FacetRenameInfo info : toRename) {
        myDispatcher.getMulticaster().beforeFacetRenamed(info.myFacet);
      }

      for (FacetRenameInfo info : toRename) {
        info.myFacet.setName(info.myNewName);
      }
      myModel.setAllFacets(newFacets.toArray(new Facet[newFacets.size()]));

      for (Facet facet : toAdd) {
        facet.initFacet();
      }
      for (Facet facet : toRemove) {
        Disposer.dispose(facet);
      }

      for (Facet facet : toAdd) {
        myDispatcher.getMulticaster().facetAdded(facet);
      }
      for (Facet facet : toRemove) {
        myDispatcher.getMulticaster().facetRemoved(facet);
      }
      for (FacetRenameInfo info : toRename) {
        myDispatcher.getMulticaster().facetRenamed(info.myFacet, info.myOldName);
      }

    }
    finally {
      myInsideCommit = false;
    }
  }


  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {
    for (Facet facet : getAllFacets()) {
      facet.initFacet();
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FacetManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    for (Facet facet : getAllFacets()) {
      Disposer.dispose(facet);
    }
  }

  private static class FacetManagerModel extends FacetModelBase {
    private Facet[] myAllFacets = Facet.EMPTY_ARRAY;

    @NotNull
    public Facet[] getAllFacets() {
      return myAllFacets;
    }

    public void setAllFacets(final Facet[] allFacets) {
      myAllFacets = allFacets;
      facetsChanged();
    }
  }

  private static class FacetRenameInfo {
    private Facet myFacet;
    private String myOldName;
    private String myNewName;

    public FacetRenameInfo(final Facet facet, final String oldName, final String newName) {
      myFacet = facet;
      myOldName = oldName;
      myNewName = newName;
    }
  }
}
