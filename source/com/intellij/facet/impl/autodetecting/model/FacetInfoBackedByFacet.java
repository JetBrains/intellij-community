package com.intellij.facet.impl.autodetecting.model;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FacetInfoBackedByFacet implements FacetInfo2<Module> {
  private Facet myFacet;
  private final ProjectFacetInfoSet myProjectFacetSet;

  FacetInfoBackedByFacet(@NotNull Facet facet, final ProjectFacetInfoSet projectFacetSet) {
    myFacet = facet;
    myProjectFacetSet = projectFacetSet;
  }

  @NotNull
  public String getFacetName() {
    return myFacet.getName();
  }

  @NotNull
  public FacetConfiguration getConfiguration() {
    return myFacet.getConfiguration();
  }

  @NotNull
  public FacetType getFacetType() {
    return myFacet.getType();
  }

  public FacetInfo2<Module> getUnderlyingFacetInfo() {
    Facet underlying = myFacet.getUnderlyingFacet();
    return underlying != null ? myProjectFacetSet.getOrCreateInfo(underlying) : null;
  }

  @NotNull
  public Module getModule() {
    return myFacet.getModule();
  }

  public Facet getFacet() {
    return myFacet;
  }
}
