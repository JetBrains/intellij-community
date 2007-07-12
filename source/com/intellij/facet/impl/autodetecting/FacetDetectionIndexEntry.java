/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.util.SmartList;
import com.intellij.util.fileIndex.FileIndexEntry;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
*/
public class FacetDetectionIndexEntry extends FileIndexEntry {
  private SmartList<FacetPointer> myDetectedFacets;

  public FacetDetectionIndexEntry(final long timestamp) {
    super(timestamp);
  }

  public FacetDetectionIndexEntry(final DataInputStream stream, final FacetPointersManager facetPointersManager) throws IOException {
    super(stream);
    int size = stream.readInt();
    if (size > 0) {
      myDetectedFacets = new SmartList<FacetPointer>();
      while (size-- > 0) {
        myDetectedFacets.add(facetPointersManager.create(stream.readUTF()));
      }
    }
  }

  @Nullable
  public SmartList<FacetPointer> getDetectedFacets() {
    return myDetectedFacets;
  }

  public void write(final DataOutputStream stream) throws IOException {
    super.write(stream);
    if (myDetectedFacets != null) {
      stream.writeInt(myDetectedFacets.size());
      for (FacetPointer facetPointer : myDetectedFacets) {
        stream.writeUTF(facetPointer.getId());
      }
    }
    else {
      stream.writeInt(0);
    }
  }

  public boolean isEmpty() {
    return myDetectedFacets == null || myDetectedFacets.isEmpty();
  }

  @Nullable
  public Collection<FacetPointer> update(final FacetPointersManager facetPointersManager, final @Nullable List<Facet> facets) {
    if (facets == null || facets.isEmpty()) {
      SmartList<FacetPointer> old = myDetectedFacets;
      myDetectedFacets = null;
      return old;
    }

    if (myDetectedFacets == null) {
      myDetectedFacets = new SmartList<FacetPointer>();
    }

    List<Facet> removeFacets = null;
    Set<FacetPointer> toRemove = new THashSet<FacetPointer>(myDetectedFacets);
    for (Facet facet : facets) {
      FacetPointer<Facet> pointer = facetPointersManager.create(facet);
      toRemove.remove(pointer);
      if (!myDetectedFacets.contains(pointer)) {
        if (removeFacets == null) {
          removeFacets = new SmartList<Facet>();
        }
        removeFacets.add(facet);
        myDetectedFacets.add(pointer);
      }
    }

    for (FacetPointer pointer : toRemove) {
      myDetectedFacets.remove(pointer);
    }
    return toRemove;
  }

  public void remove(final FacetPointer facetPointer) {
    if (myDetectedFacets != null) {
      myDetectedFacets.remove(facetPointer);
    }
  }
}
