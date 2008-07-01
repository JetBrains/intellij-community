package com.intellij.facet.impl.autodetecting.model;

import com.intellij.facet.*;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SystemProperties;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ProjectFacetInfoSet extends FacetInfoSet<Module> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.autodetecting.model.ProjectFacetInfoSet");
  private Map<Facet, FacetInfoBackedByFacet> myFacetInfos = new HashMap<Facet, FacetInfoBackedByFacet>();
  private EventDispatcher<DetectedFacetListener> myDispatcher = EventDispatcher.create(DetectedFacetListener.class);
  private int myNextId;
  private final Project myProject;

  public ProjectFacetInfoSet(Project project, Disposable parentDisposable) {
    myProject = project;
    ProjectWideFacetListenersRegistry.getInstance(project).registerListener(new MyProjectWideFacetAdapter(), parentDisposable);
  }

  public FacetInfoBackedByFacet getOrCreateInfo(@NotNull Facet facet) {
    FacetInfoBackedByFacet info = myFacetInfos.get(facet);
    if (info == null) {
      info = new FacetInfoBackedByFacet(facet, this);
      myFacetInfos.put(facet, info);
    }
    return info;
  }

  public void addFacetInfo(@NotNull final FacetInfo2<Module> facetInfo) {
    super.addFacetInfo(facetInfo);
    if (facetInfo instanceof DetectedFacetInfo) {
      myDispatcher.getMulticaster().facetDetected((DetectedFacetInfo<Module>)facetInfo);
    }
  }

  public void removeFacetInfo(@NotNull final FacetInfo2<Module> facetInfo) {
    super.removeFacetInfo(facetInfo);
    if (facetInfo instanceof DetectedFacetInfo) {
      myDispatcher.getMulticaster().facetRemoved((DetectedFacetInfo<Module>)facetInfo);
    }
  }

  public void loadDetectedFacets(@NotNull File file) {
    if (!file.exists()) return;

    FacetTypeRegistry facetTypeRegistry = FacetTypeRegistry.getInstance();
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);

    Document document;
    try {
      document = JDOMUtil.loadDocument(file);
    }
    catch (Exception e) {
      LOG.info(e);
      return;
    }

    DetectedFacetsBean detectedFacetsBean = XmlSerializer.deserialize(document, DetectedFacetsBean.class);
    if (detectedFacetsBean == null) return;
    
    for (DetectedFacetBean facetBean : detectedFacetsBean.myFacets) {
      FacetType type = facetTypeRegistry.findFacetType(facetBean.myTypeId);
      if (type == null) {
        LOG.info("facet type '" + facetBean.myTypeId + " not found");
        continue;
      }
      Module module = moduleManager.findModuleByName(facetBean.myModuleName);
      if (module == null) {
        LOG.info("module '" + facetBean.myModuleName + "' not found");
        continue;
      }

      FacetInfo2<Module> underlyingFacet;
      String underlyingId = facetBean.myUnderlyingId;
      if (underlyingId.startsWith("/")) {
        underlyingFacet = findById(Integer.parseInt(underlyingId.substring(1)));
      }
      else {
        Facet facet = FacetPointersManager.getInstance(myProject).create(underlyingId).getFacet();
        underlyingFacet = facet != null ? getOrCreateInfo(facet) : null;
      }
      FacetConfiguration configuration = type.createDefaultConfiguration();
      try {
        FacetUtil.loadFacetConfiguration(configuration, facetBean.myConfiguration);
      }
      catch (InvalidDataException e) {
        LOG.info(e);
        continue;
      }

      int id = facetBean.myId;
      myNextId = Math.max(myNextId, id + 1);
      addFacetInfo(new DetectedFacetInfoImpl<Module>(facetBean.myFacetName, configuration, type, module, underlyingFacet, null, id,
                                                         facetBean.myDetectorId));
    }
  }

  public void saveDetectedFacets(File file) {
    DetectedFacetsBean facetsBean = new DetectedFacetsBean();
    Collection<DetectedFacetInfo<Module>> facets = getAllDetectedFacets();
    Set<DetectedFacetInfo<Module>> added = new HashSet<DetectedFacetInfo<Module>>();
    for (DetectedFacetInfo<Module> facet : facets) {
      addFacetBean(facet, facetsBean, added);
    }

    Element element = XmlSerializer.serialize(facetsBean, new SkipDefaultValuesSerializationFilters());
    try {
      JDOMUtil.writeDocument(new Document(element), file, SystemProperties.getLineSeparator());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private static boolean addFacetBean(final DetectedFacetInfo<Module> facet, final DetectedFacetsBean facetsBean,
                            final Set<DetectedFacetInfo<Module>> added) {
    if (!added.add(facet)) {
      return true;
    }

    FacetInfo2<Module> underlyingInfo = facet.getUnderlyingFacetInfo();
    if (underlyingInfo instanceof DetectedFacetInfo && !addFacetBean((DetectedFacetInfo<Module>)underlyingInfo, facetsBean, added)) {
      return false;
    }

    try {
      final Element configuration = FacetUtil.saveFacetConfiguration(facet.getConfiguration());
      final String underlyingId;
      if (underlyingInfo instanceof FacetInfoBackedByFacet) {
        underlyingId = FacetPointersManager.constructId(((FacetInfoBackedByFacet)underlyingInfo).getFacet());
      }
      else {
        underlyingId = underlyingInfo != null ? "/" + ((DetectedFacetInfo)underlyingInfo).getId() : null;
      }
      DetectedFacetBean facetBean = new DetectedFacetBean(facet.getId(), facet.getFacetName(), facet.getModule().getName(), configuration,
                                                          facet.getFacetType().getStringId(), underlyingId, facet.getDetectorId());
      facetsBean.myFacets.add(facetBean);
      return true;
    }
    catch (WriteExternalException e) {
      LOG.info(e);
      return false;
    }
  }

  public void addListener(@NotNull DetectedFacetListener listener) {
    myDispatcher.addListener(listener);
  }

  public <C extends FacetConfiguration, F extends Facet<C>> DetectedFacetInfo<Module> createInfo(final Module module,
                                                                                                 final String url, final FacetInfo2<Module> underlyingFacet,
                                                                                                 final C detectedConfiguration,
                                                                                                 final String name,
                                                                                                 final FacetType<F, C> facetType,
                                                                                                 final String detectorId) {
    return new DetectedFacetInfoImpl<Module>(name, detectedConfiguration, facetType, module, underlyingFacet, url,
                                                                               generateId(), detectorId);
  }

  private int generateId() {
    return myNextId++;
  }

  private class MyProjectWideFacetAdapter extends ProjectWideFacetAdapter<Facet> {
    public void facetAdded(final Facet facet) {
      addFacetInfo(getOrCreateInfo(facet));
    }

    public void facetRemoved(final Facet facet) {
      FacetInfoBackedByFacet detected = myFacetInfos.remove(facet);
      if (detected != null) {
        removeFacetInfo(detected);
      }
    }
  }

  public static interface DetectedFacetListener extends EventListener {
    void facetDetected(final DetectedFacetInfo<Module> info);
    void facetRemoved(DetectedFacetInfo<Module> info);
  }


  public static class DetectedFacetsBean {
    @Tag("facets")
    @AbstractCollection(surroundWithTag = false)
    public List<DetectedFacetBean> myFacets = new ArrayList<DetectedFacetBean>();
  }

  @Tag("facet")
  public static class DetectedFacetBean {
    @Attribute("type")
    public String myTypeId;

    @Attribute("facet-name")
    public String myFacetName;

    @Attribute("id")
    public int myId;

    @Tag("configuration")
    public Element myConfiguration;

    @Attribute("underlying-id")
    public String myUnderlyingId;

    @Attribute("module-name")
    public String myModuleName;

    @Attribute("detector-id")
    public String myDetectorId;

    public DetectedFacetBean() {
    }

    public DetectedFacetBean(final int id, final String facetName, final String moduleName, final Element configuration,
                             final String typeId, final String underlyingId, String detectorId) {
      myId = id;
      myFacetName = facetName;
      myConfiguration = configuration;
      myTypeId = typeId;
      myModuleName = moduleName;
      myUnderlyingId = underlyingId;
      myDetectorId = detectorId;
    }
  }
}
