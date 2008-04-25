package com.intellij.facet.impl;

import com.intellij.facet.FacetManagerImpl;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
*/
@Tag(FacetManagerImpl.FACET_ELEMENT)
public class FacetState {
  private String myFacetType;
  private String myName;
  private boolean myImplicit = false;
  private Element myConfiguration;
  private List<FacetState> mySubFacets = new ArrayList<FacetState>();

  @Attribute(FacetManagerImpl.TYPE_ATTRIBUTE)
  public String getFacetType() {
    return myFacetType;
  }

  @Attribute(FacetManagerImpl.NAME_ATTRIBUTE)
  public String getName() {
    return myName;
  }

  @Attribute("implicit")
  public boolean isImplicit() {
    return myImplicit;
  }

  @Tag(FacetManagerImpl.CONFIGURATION_ELEMENT)
  public Element getConfiguration() {
    return myConfiguration;
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<FacetState> getSubFacets() {
    return mySubFacets;
  }

  public void setSubFacets(final List<FacetState> subFacets) {
    mySubFacets = subFacets;
  }

  public void setConfiguration(final Element configuration) {
    myConfiguration = configuration;
  }

  public void setName(final String name) {
    myName = name;
  }

  public void setFacetType(final String type) {
    myFacetType = type;
  }

  public void setImplicit(final boolean implicit) {
    myImplicit = implicit;
  }
}
