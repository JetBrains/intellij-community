/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.BidirectionalMultiMap;
import com.intellij.util.fileIndex.AbstractFileIndex;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * @author nik
 */
public class FacetDetectionIndex extends AbstractFileIndex<FacetDetectionIndexEntry> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.autodetecting.FacetDetectionIndex");
  private static final byte CURRENT_VERSION = 0;
  @NonNls private static final String CACHE_DIRECTORY_NAME = "facets";
  private final FileTypeManager myFileTypeManager;
  private final Set<FileType> myFileTypes;
  private Set<FacetType> myNewFacetTypes = new THashSet<FacetType>();
  private FacetPointersManager myFacetPointersManager;
  private final FacetAutodetectingManagerImpl myAutodetectingManager;
  private final BidirectionalMultiMap<String, FacetPointer> myDetectedFacets;

  public FacetDetectionIndex(final Project project, final FacetAutodetectingManagerImpl autodetectingManager, Set<FileType> fileTypes) {
    super(project);
    myAutodetectingManager = autodetectingManager;
    myFileTypes = new THashSet<FileType>(fileTypes);
    myFileTypeManager = FileTypeManager.getInstance();
    myFacetPointersManager = FacetPointersManager.getInstance(project);
    myDetectedFacets = new BidirectionalMultiMap<String, FacetPointer>();
  }

  protected FacetDetectionIndexEntry createIndexEntry(final DataInputStream input) throws IOException {
    return new FacetDetectionIndexEntry(input, myFacetPointersManager);
  }

  public boolean belongs(final VirtualFile file) {
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    return myFileTypes.contains(fileType);
  }

  protected String getLoadingIndicesMessage() {
    return ProjectBundle.message("progress.text.loading.facet.detection.indices");
  }

  protected String getBuildingIndicesMessage(final boolean formatChanged) {
    return formatChanged ? ProjectBundle.message("progress.text.facet.indices.format.has.changed.redetecting.facets") 
           : ProjectBundle.message("progress.text.detecting.facets");
  }

  public byte getCurrentVersion() {
    return CURRENT_VERSION;
  }

  public String getCachesDirName() {
    return CACHE_DIRECTORY_NAME;
  }

  protected void readHeader(final DataInputStream input) throws IOException {
    int size = input.readInt();
    Set<String> facetTypesInCache = new THashSet<String>();
    while (size-- > 0) {
      facetTypesInCache.add(input.readUTF());
    }
    Set<String> unknownTypes = new THashSet<String>(facetTypesInCache);
    for (FacetType type : FacetTypeRegistry.getInstance().getFacetTypes()) {
      unknownTypes.remove(type.getStringId());
      if (!facetTypesInCache.contains(type.getStringId())) {
        myNewFacetTypes.add(type);
      }
    }
    LOG.info("Unknown facet types in cache: " + unknownTypes);
  }

  @Nullable
  protected Set<FileType> getFileTypesToRefresh() {
    if (myNewFacetTypes.isEmpty()) {
      return null;
    }

    return myAutodetectingManager.getFileTypes(myNewFacetTypes);
  }

  protected void writeHeader(final DataOutputStream output) throws IOException {
    FacetType[] types = FacetTypeRegistry.getInstance().getFacetTypes();
    output.writeInt(types.length);
    for (FacetType type : types) {
      output.writeUTF(type.getStringId());
    }
  }

  public void queueEntryUpdate(final VirtualFile file) {
    myAutodetectingManager.queueUpdate(file);
  }

  protected void doUpdateIndexEntry(final VirtualFile file) {
    myAutodetectingManager.processFile(file);
  }

  @Nullable
  public Set<String> getFiles(final FacetPointer pointer) {
    return myDetectedFacets.getKeys(pointer);
  }

  @Nullable
  public Set<String> getFiles(final Facet facet) {
    return myDetectedFacets.getKeys(myFacetPointersManager.create(facet));
  }

  protected void onEntryAdded(final String url, final FacetDetectionIndexEntry entry) {
    myDetectedFacets.removeKey(url);
    SmartList<FacetPointer> detectedFacets = entry.getDetectedFacets();
    if (detectedFacets != null) {
      for (FacetPointer detectedFacet : detectedFacets) {
        myDetectedFacets.put(url, detectedFacet);
      }
    }
  }

  protected void onEntryRemoved(final String url, final FacetDetectionIndexEntry entry) {
    Set<FacetPointer> pointers = myDetectedFacets.getValues(url);
    myDetectedFacets.removeKey(url);
    if (pointers != null && !pointers.isEmpty()) {
      myAutodetectingManager.removeObsoleteFacets(pointers);
    }
  }

  public void removeFacetFromCache(final FacetPointer<Facet> facetPointer) {
    Set<String> urls = myDetectedFacets.getKeys(facetPointer);
    if (urls != null) {
      for (String url : urls) {
        FacetDetectionIndexEntry indexEntry = getIndexEntry(url);
        if (indexEntry != null) {
          indexEntry.remove(facetPointer);
        }
      }
    }
  }
}
