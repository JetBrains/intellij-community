package com.intellij.facet.impl.autodetecting.model;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author nik
 */
public class DetectedFacetInfoImpl<M> implements DetectedFacetInfo<M> {
  private final String myFacetName;
  private final FacetConfiguration myConfiguration;
  private final FacetType<?,?> myFacetType;
  private final FacetInfo2<M> myUnderlyingFacetInfo;
  private final M myModule;
  private final Collection<String> myUrls;
  private final int myId;
  private final String myDetectorId;

  public DetectedFacetInfoImpl(final @NotNull String facetName, final @NotNull FacetConfiguration configuration, final @NotNull FacetType<?, ?> facetType,
                               final @NotNull M module,
                               final @Nullable FacetInfo2<M> underlyingFacetInfo,
                               final String url,
                               final int id,
                               final String detectorId) {
    myFacetName = facetName;
    myConfiguration = configuration;
    myFacetType = facetType;
    myModule = module;
    myUnderlyingFacetInfo = underlyingFacetInfo;
    myId = id;
    myDetectorId = detectorId;
    myUrls = new ArrayList<String>();
    myUrls.add(url);
  }

  @NotNull
  public String getFacetName() {
    return myFacetName;
  }

  @NotNull
  public FacetConfiguration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public FacetType<?,?> getFacetType() {
    return myFacetType;
  }

  public FacetInfo2<M> getUnderlyingFacetInfo() {
    return myUnderlyingFacetInfo;
  }

  @NotNull
  public M getModule() {
    return myModule;
  }

  public int getId() {
    return myId;
  }

  public String getDetectorId() {
    return myDetectorId;
  }
}
