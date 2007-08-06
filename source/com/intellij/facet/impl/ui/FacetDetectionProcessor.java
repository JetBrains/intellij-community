/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.*;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.impl.autodetecting.FacetDetectorForWizardRegistry;
import com.intellij.facet.impl.autodetecting.FacetDetectorRegistryEx;
import com.intellij.facet.impl.autodetecting.UnderlyingFacetSelector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class FacetDetectionProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.FacetDetectionProcessor");
  private Map<FacetConfiguration, Pair<FacetInfo, VirtualFile>> myDetectedFacets = new LinkedHashMap<FacetConfiguration, Pair<FacetInfo, VirtualFile>>();
  private Map<FacetTypeId, List<FacetConfiguration>> myDetectedConfigurations = new HashMap<FacetTypeId, List<FacetConfiguration>>();
  private final ProgressIndicator myProgressIndicator;
  private FileTypeManager myFileTypeManager;
  private List<MultiValuesMap<FileType, MyFacetDetectorWrapper>> myDetectors = new ArrayList<MultiValuesMap<FileType, MyFacetDetectorWrapper>>();

  public FacetDetectionProcessor(final ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
    myFileTypeManager = FileTypeManager.getInstance();
    FacetType[] types = FacetTypeRegistry.getInstance().getFacetTypes();
    for (FacetType<?,?> type : types) {
      registerDetectors(type);
      myDetectedConfigurations.put(type.getId(), new ArrayList<FacetConfiguration>());
    }
  }

  private <C extends FacetConfiguration> void registerDetectors(final FacetType<?, C> type) {
    type.registerDetectors(new FacetDetectorRegistryEx<C>(new MyFacetDetectorRegistry<C>(type), null));
  }

  public void process(final File root) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(root);
    if (virtualFile == null) return;

    for (int i = 0; i < myDetectors.size(); i++) {
      MultiValuesMap<FileType, MyFacetDetectorWrapper> map = myDetectors.get(i);
      if (i > 0) {
        List<MyFacetDetectorWrapper> toRemove = new ArrayList<MyFacetDetectorWrapper>();
        for (MyFacetDetectorWrapper detector : map.values()) {
          FacetTypeId typeId = detector.getFacetType().getUnderlyingFacetType();
          LOG.assertTrue(typeId != null);
          List<FacetConfiguration> list = myDetectedConfigurations.get(typeId);
          if (list == null || list.isEmpty()) {
            toRemove.add(detector);
          }
        }

        for (MyFacetDetectorWrapper detectorWrapper : toRemove) {
          map.remove(detectorWrapper.getFileType(), detectorWrapper);
        }
      }

      if (map.isEmpty()) break;
      process(virtualFile, map);
    }
  }

  private void process(final VirtualFile file, MultiValuesMap<FileType, MyFacetDetectorWrapper> detectorsMap) {
    if (myProgressIndicator.isCanceled()) return;

    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        process(child, detectorsMap);
      }
      return;
    }

    myProgressIndicator.setText2(file.getPresentableUrl());
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    Collection<MyFacetDetectorWrapper> detectors = detectorsMap.get(fileType);
    if (detectors == null) return;

    for (MyFacetDetectorWrapper detector : detectors) {
      detector.detectFacet(file);
    }
  }

  public List<Pair<FacetInfo, VirtualFile>> getDetectedFacetsWithFiles() {
    return new ArrayList<Pair<FacetInfo, VirtualFile>>(myDetectedFacets.values());
  }

  public List<FacetInfo> getDetectedFacets() {
    List<FacetInfo> list = new ArrayList<FacetInfo>();
    for (Pair<FacetInfo, VirtualFile> pair : myDetectedFacets.values()) {
      list.add(pair.getFirst());
    }
    return list;
  }

  private class MyFacetDetectorWrapper<C extends FacetConfiguration, U extends FacetConfiguration> {
    private final FacetType<?, C> myFacetType;
    private final FileType myFileType;
    private VirtualFileFilter myVirtualFileFilter;
    private FacetDetector<VirtualFile, C> myDetector;
    private UnderlyingFacetSelector<VirtualFile, U> myUnderlyingFacetSelector;

    public MyFacetDetectorWrapper(final FacetType<?, C> facetType, final FileType fileType, final VirtualFileFilter virtualFileFilter, final FacetDetector<VirtualFile, C> detector,
                                  final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
      myUnderlyingFacetSelector = underlyingFacetSelector;
      myFacetType = facetType;
      myFileType = fileType;
      myVirtualFileFilter = virtualFileFilter;
      myDetector = detector;
    }

    public FileType getFileType() {
      return myFileType;
    }

    public FacetType<?, C> getFacetType() {
      return myFacetType;
    }

    public void detectFacet(VirtualFile file) {
      if (!myVirtualFileFilter.accept(file)) return;

      FacetInfo underlyingFacet = null;
      if (myUnderlyingFacetSelector != null) {
        List<U> list = (List<U>)myDetectedConfigurations.get(myFacetType.getUnderlyingFacetType());
        U underlying = myUnderlyingFacetSelector.selectUnderlyingFacet(file, list);
        if (underlying == null) {
          return;
        }
        underlyingFacet = myDetectedFacets.get(underlying).getFirst();
      }

      List<C> configurations = (List<C>)myDetectedConfigurations.get(myFacetType.getId());
      C newConfiguration = myDetector.detectFacet(file, configurations);
      if (newConfiguration == null || configurations.contains(newConfiguration)) {
        return;
      }

      FacetInfo facetInfo = new FacetInfo(myFacetType, generateFacetName(), newConfiguration, underlyingFacet);
      configurations.add(newConfiguration);
      myDetectedFacets.put(newConfiguration, Pair.create(facetInfo, file));
    }

    private String generateFacetName() {
      String baseName = myFacetType.getDefaultFacetName();

      String name = baseName;
      int i = 2;
      while (isUsed(name)) {
        name = baseName + i;
        i++;
      }

      return name;
    }

    private boolean isUsed(final String name) {
      List<FacetConfiguration> configurations = myDetectedConfigurations.get(myFacetType.getId());
      if (configurations != null) {
        for (FacetConfiguration configuration : configurations) {
          if (name.equals(myDetectedFacets.get(configuration).getFirst().getName())) {
            return true;
          }
        }
      }
      return false;
    }

  }

  private class MyFacetDetectorRegistry<C extends FacetConfiguration> implements FacetDetectorForWizardRegistry<C> {
    private final FacetType<?, C> myFacetType;
    private int myLevel;

    public MyFacetDetectorRegistry(final FacetType<?, C> facetType) {
      myFacetType = facetType;
      myLevel = 0;
      FacetTypeId<?> typeId = facetType.getUnderlyingFacetType();
      Set<FacetTypeId> parentTypes = new HashSet<FacetTypeId>();
      parentTypes.add(facetType.getId());
      while (typeId != null) {
        myLevel++;
        FacetType<?,?> underlying = FacetTypeRegistry.getInstance().findFacetType(typeId);
        LOG.assertTrue(underlying != null, "Cannot find facet type by id: " + typeId);
        typeId = underlying.getUnderlyingFacetType();
        if (!parentTypes.add(typeId)) {
          LOG.error("Circular dependency between facets: " + parentTypes);
        }
      }
    }

    public void register(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter,
                         @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
      LOG.assertTrue(myFacetType.getUnderlyingFacetType() == null, "This method must not be used for sub-facets");
      getDetectorsMap().put(fileType, new MyFacetDetectorWrapper<C, FacetConfiguration>(myFacetType, fileType, virtualFileFilter,
                                                                                        facetDetector, null));
    }

    private MultiValuesMap<FileType, MyFacetDetectorWrapper> getDetectorsMap() {
      while (myLevel >= myDetectors.size()) {
        myDetectors.add(new MultiValuesMap<FileType, MyFacetDetectorWrapper>());
      }
      return myDetectors.get(myLevel);
    }

    public <U extends FacetConfiguration> void register(final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, final FacetDetector<VirtualFile, C> facetDetector,
                                                        final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
      LOG.assertTrue(myFacetType.getUnderlyingFacetType() != null, "This method can be used only for sub-facets");
      MyFacetDetectorWrapper<C, U> detector = new MyFacetDetectorWrapper<C, U>(myFacetType, fileType, virtualFileFilter,
                                                                               facetDetector, underlyingFacetSelector);
      getDetectorsMap() .put(fileType, detector);
    }
  }
}
