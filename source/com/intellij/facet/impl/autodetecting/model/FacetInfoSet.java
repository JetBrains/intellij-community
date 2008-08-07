package com.intellij.facet.impl.autodetecting.model;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class FacetInfoSet<M> {
  private final Map<FacetConfiguration, FacetInfo2<M>> myFacetInfos = new HashMap<FacetConfiguration, FacetInfo2<M>>();
  private final Map<FacetTypeId, MultiValuesMap<M, FacetInfo2<M>>> myInfosByType = new FactoryMap<FacetTypeId, MultiValuesMap<M, FacetInfo2<M>>>() {
    protected MultiValuesMap<M, FacetInfo2<M>> create(final FacetTypeId key) {
      return new MultiValuesMap<M, FacetInfo2<M>>();
    }
  };
  private final Map<Integer, DetectedFacetInfo<M>> myDetectedById = new HashMap<Integer, DetectedFacetInfo<M>>();

  public void addFacetInfo(@NotNull FacetInfo2<M> facetInfo) {
    myFacetInfos.put(facetInfo.getConfiguration(), facetInfo);
    myInfosByType.get(facetInfo.getFacetType().getId()).put(facetInfo.getModule(), facetInfo);
    if (facetInfo instanceof DetectedFacetInfo) {
      DetectedFacetInfo<M> detected = (DetectedFacetInfo<M>)facetInfo;
      myDetectedById.put(detected.getId(), detected);
    }
  }

  public void removeFacetInfo(@NotNull FacetInfo2<M> facetInfo) {
    myFacetInfos.remove(facetInfo.getConfiguration());
    myInfosByType.get(facetInfo.getFacetType().getId()).remove(facetInfo.getModule(), facetInfo);
    if (facetInfo instanceof DetectedFacetInfo) {
      final DetectedFacetInfo<M> detected = (DetectedFacetInfo<M>)facetInfo;
      myDetectedById.remove(detected.getId());
    }
  }

  public void removeDetectedFacets(final FacetTypeId<?> type, final M module) {
    List<FacetInfo2<M>> infos = new ArrayList<FacetInfo2<M>>(myInfosByType.get(type).get(module));
    for (FacetInfo2<M> info : infos) {
      if (info instanceof DetectedFacetInfo) {
        removeFacetInfo(info);
      }
    }
  }

  public void removeDetectedFacets(FacetTypeId<?> type) {
    List<FacetInfo2<M>> infos = new ArrayList<FacetInfo2<M>>(myInfosByType.get(type).values());
    for (FacetInfo2<M> info : infos) {
      if (info instanceof DetectedFacetInfo) {
        removeFacetInfo(info);
      }
    }
  }

  @Nullable
  public FacetInfo2 findFacet(final @NotNull FacetTypeId<?> typeId, @NotNull M module, final @NotNull String name) {
    Collection<FacetInfo2<M>> detectedFacets = myInfosByType.get(typeId).get(module);
    if (detectedFacets != null) {
      for (FacetInfo2 detected : detectedFacets) {
        if (detected.getFacetName().equals(name)) {
          return detected;
        }
      }
    }
    return null;
  }

  public <C extends FacetConfiguration, F extends Facet<C>> Map<C, FacetInfo2<M>> getConfigurations(final FacetTypeId<F> typeId, final M module) {
    //todo[nik] cache?
    Map<C, FacetInfo2<M>> configurations = new LinkedHashMap<C, FacetInfo2<M>>();
    Collection<FacetInfo2<M>> detectedFacets = myInfosByType.get(typeId).get(module);
    if (detectedFacets != null) {
      for (FacetInfo2 detected : detectedFacets) {
        if (detected instanceof FacetInfoBackedByFacet) {
          //noinspection unchecked
          configurations.put((C)detected.getConfiguration(), detected);
        }
      }
      for (FacetInfo2 detected : detectedFacets) {
        if (!(detected instanceof FacetInfoBackedByFacet)) {
          //noinspection unchecked
          configurations.put((C)detected.getConfiguration(), detected);
        }
      }
    }
    return configurations;
  }

  public String generateName(final M module, final FacetType<?, ?> facetType) {
    String baseName = facetType.getDefaultFacetName();
    int i = 2;
    String name = baseName;
    while (findFacet(facetType.getId(), module, name) != null) {
      name = baseName + i;
      i++;
    }
    return name;
  }

  @Nullable
  public FacetInfo2<M> findById(int id) {
    return myDetectedById.get(id);
  }

  public void removeDetectedFacetWithSubFacets(final Integer id) {
    FacetInfo2<M> detected = myDetectedById.get(id);
    if (detected != null) {
      Set<FacetInfo2<M>> set = collectSubFacets(detected);
      for (FacetInfo2<M> info : set) {
        removeFacetInfo(info);
      }
    }
  }

  public void removeDetectedFacets(@NotNull M module) {
    List<FacetTypeId> typeIds = new ArrayList<FacetTypeId>(myInfosByType.keySet());
    for (FacetTypeId typeId : typeIds) {
      removeDetectedFacets(typeId, module);
    }
  }

  private Set<FacetInfo2<M>> collectSubFacets(final FacetInfo2<M> facetInfo) {
    final Set<FacetInfo2<M>> set = new HashSet<FacetInfo2<M>>();
    set.add(facetInfo);
    boolean setChanged = true;
    while (setChanged) {
      setChanged = false;
      for (FacetInfo2<M> info : myFacetInfos.values()) {
        FacetInfo2<M> underlying = info.getUnderlyingFacetInfo();
        if (underlying != null && set.contains(underlying) && !set.contains(info)) {
          set.add(info);
          setChanged = true;
        }
      }
    }
    return set;
  }

  public Collection<DetectedFacetInfo<M>> getAllDetectedFacets() {
    return Collections.unmodifiableCollection(myDetectedById.values());
  }

}
