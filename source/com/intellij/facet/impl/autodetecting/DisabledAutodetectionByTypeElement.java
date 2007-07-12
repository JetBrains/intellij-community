/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author nik
*/
@Tag("facet-type")
public class DisabledAutodetectionByTypeElement {
  private String myFacetTypeId;
  private List<DisabledAutodetectionInModuleElement> myModuleElements = new ArrayList<DisabledAutodetectionInModuleElement>();
  
  public DisabledAutodetectionByTypeElement() {
  }

  public DisabledAutodetectionByTypeElement(final String facetTypeId) {
    myFacetTypeId = facetTypeId;
  }

  public DisabledAutodetectionByTypeElement(String facetTypeId, String moduleName) {
    this(facetTypeId);
    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName));
  }

  public DisabledAutodetectionByTypeElement(String facetTypeId, String moduleName, String url) {
    this(facetTypeId);
    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName, url));
  }

  @Attribute("id")
  public String getFacetTypeId() {
    return myFacetTypeId;
  }

  @Tag("modules")
  @AbstractCollection(surroundWithTag = false)
  public List<DisabledAutodetectionInModuleElement> getModuleElements() {
    return myModuleElements;
  }

  public void setFacetTypeId(final String facetTypeId) {
    myFacetTypeId = facetTypeId;
  }

  public void setModuleElements(final List<DisabledAutodetectionInModuleElement> moduleElements) {
    myModuleElements = moduleElements;
  }

  public void addDisabled(@NotNull String moduleName) {
    if (myModuleElements.isEmpty()) return;

    DisabledAutodetectionInModuleElement element = findElement(moduleName);
    if (element != null) {
      element.getFiles().clear();
      return;
    }

    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName));
  }

  public void disableInProject() {
    myModuleElements.clear();
  }

  public void addDisabled(@NotNull String moduleName, @NotNull String fileUrl) {
    if (myModuleElements.isEmpty()) return;

    DisabledAutodetectionInModuleElement element = findElement(moduleName);
    if (element != null) {
      if (!element.getFiles().isEmpty()) {
        element.getFiles().add(fileUrl);
      }
      return;
    }
    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName, fileUrl));
  }

  @Nullable
  private DisabledAutodetectionInModuleElement findElement(final @NotNull String moduleName) {
    for (DisabledAutodetectionInModuleElement element : myModuleElements) {
      if (moduleName.equals(element.getModuleName())) {
        return element;
      }
    }
    return null;
  }

  public boolean isDisabled(final String moduleName, final String url) {
    if (myModuleElements.isEmpty()) return true;

    DisabledAutodetectionInModuleElement element = findElement(moduleName);
    if (element == null) return false;

    Set<String> files = element.getFiles();
    return files.isEmpty() || files.contains(url);
  }
}
