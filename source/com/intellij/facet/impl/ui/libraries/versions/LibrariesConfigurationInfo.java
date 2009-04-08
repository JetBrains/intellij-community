package com.intellij.facet.impl.ui.libraries.versions;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;

public class LibrariesConfigurationInfo {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public LibraryConfigurationInfo[] myInfos;

  @Attribute("default-version")
  public String myDefaultVersion;

  @Attribute("default-ri")
  public String myDefaultRI;

  @Attribute("default-download-url")
  public String myDefaultDownloadUrl;

  @Attribute("default-presentation-url")
  public String myDefaultPresentationUrl;

  public LibraryConfigurationInfo[] getLibraryConfigurationInfos() {
    return myInfos;
  }

  public String getDefaultVersion() {
    return myDefaultVersion;
  }

  public String getDefaultDownloadUrl() {
    return myDefaultDownloadUrl;
  }

  public String getDefaultPresentationUrl() {
    return myDefaultPresentationUrl;
  }

  public String getDefaultRI() {
    return myDefaultRI;
  }
}