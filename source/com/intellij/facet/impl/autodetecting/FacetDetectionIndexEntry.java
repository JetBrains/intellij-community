/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.autodetecting.model.DetectedFacetInfo;
import com.intellij.facet.impl.autodetecting.model.FacetInfo2;
import com.intellij.facet.impl.autodetecting.model.FacetInfoBackedByFacet;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.module.Module;
import com.intellij.util.SmartList;
import com.intellij.util.fileIndex.FileIndexEntry;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
*/
public class FacetDetectionIndexEntry extends FileIndexEntry {
  private SmartList<FacetPointer> myFacets;
  private SmartList<Integer> myDetectedFacetIds;

  public FacetDetectionIndexEntry(final long timestamp) {
    super(timestamp);
  }

  public FacetDetectionIndexEntry(final DataInputStream stream, final FacetPointersManager facetPointersManager) throws IOException {
    super(stream);
    int size = stream.readInt();
    if (size > 0) {
      while (size-- > 0) {
        String id = stream.readUTF();
        if (id.startsWith("/")) {
          if (myDetectedFacetIds == null) {
            myDetectedFacetIds = new SmartList<Integer>();
          }
          myDetectedFacetIds.add(Integer.parseInt(id.substring(1)));
        }
        else {
          if (myFacets == null) {
            myFacets = new SmartList<FacetPointer>();
          }
          myFacets.add(facetPointersManager.create(id));
        }
      }
    }
  }

  @Nullable
  public SmartList<FacetPointer> getFacets() {
    return myFacets;
  }

  @Nullable
  public SmartList<Integer> getDetectedFacetIds() {
    return myDetectedFacetIds;
  }

  public void write(final DataOutputStream stream) throws IOException {
    super.write(stream);
    int number = (myFacets != null ? myFacets.size() : 0) + (myDetectedFacetIds != null ? myDetectedFacetIds.size() : 0);
    stream.writeInt(number);
    if (myFacets != null) {
      for (FacetPointer facetPointer : myFacets) {
        stream.writeUTF(facetPointer.getId());
      }
    }
    if (myDetectedFacetIds != null) {
      for (Integer id : myDetectedFacetIds) {
        stream.writeUTF("/" + id);
      }
    }
  }

  @Nullable
  public Collection<Integer> update(final FacetPointersManager facetPointersManager, final @Nullable List<FacetInfo2<Module>> detectedFacets) {
    if (detectedFacets == null || detectedFacets.isEmpty()) {
      SmartList<Integer> old = myDetectedFacetIds;
      myFacets = null;
      myDetectedFacetIds = null;
      return old;
    }

    if (myFacets == null) {
      myFacets = new SmartList<FacetPointer>();
    }
    else {
      myFacets.clear();
    }

    if (myDetectedFacetIds == null) {
      myDetectedFacetIds = new SmartList<Integer>();
    }

    Set<Integer> toRemove = new HashSet<Integer>(myDetectedFacetIds);
    for (FacetInfo2<Module> info : detectedFacets) {
      if (info instanceof FacetInfoBackedByFacet) {
        FacetPointer<Facet> pointer = facetPointersManager.create(((FacetInfoBackedByFacet)info).getFacet());
        myFacets.add(pointer);
      }
      else {
        Integer id = ((DetectedFacetInfo)info).getId();
        boolean removed = toRemove.remove(id);
        if (!removed) {
          myDetectedFacetIds.add(id);
        }
      }
    }
    myDetectedFacetIds.removeAll(toRemove);
    return toRemove;
  }

  public void remove(final FacetPointer facetPointer) {
    if (myFacets != null) {
      myFacets.remove(facetPointer);
    }
  }

  public void remove(final Integer id) {
    if (myDetectedFacetIds != null) {
      myDetectedFacetIds.remove(id);
    }
  }

  public void add(final FacetPointer<Facet> pointer) {
    if (myFacets == null) {
      myFacets = new SmartList<FacetPointer>();
    }
    myFacets.add(pointer);
  }
}
